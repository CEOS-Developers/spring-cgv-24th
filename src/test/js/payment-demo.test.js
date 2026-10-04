const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const sourcePath = path.resolve(__dirname, '../../main/resources/static/payment-demo.js');

class Element {
  constructor(tag = 'div') {
    this.tagName = tag;
    this.value = '';
    this.textContent = '';
    this.dataset = {};
    this.children = [];
    this.listeners = {};
    this.disabled = false;
    this.checked = false;
  }
  addEventListener(type, callback) { this.listeners[type] = callback; }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children = nodes; }
  async fire(type = 'click') { await this.listeners[type]?.({ preventDefault() {} }); }
}

function boot({ screen = 'demo', state = {}, search = '', fetchHandler = () => ({}) } = {}) {
  const elements = new Map();
  const ids = ['notice', 'login-form', 'signup-form', 'login-id', 'password', 'signup-id',
    'signup-name', 'signup-email', 'signup-password', 'auth-state', 'logout', 'screening-id',
    'screening-description', 'seats', 'hold-seats', 'create-order', 'release-hold', 'open-payment',
    'order-summary', 'payment-summary', 'payment-seats', 'cancel-selected', 'cancel-remaining',
    'cancel-reason', 'operation-summary', 'refresh-operation', 'reconcile-operation', 'retry-request',
    'payment-id', 'load-payment', 'retry-confirm'];
  ids.forEach(id => elements.set(id, new Element(id.includes('form') ? 'form' : 'div')));
  const storage = new Map([['cgv-payment-demo', JSON.stringify(state)]]);
  const calls = [];
  const sdkCalls = [];
  const document = {
    body: { dataset: { screen } },
    getElementById: id => elements.get(id),
    createElement: tag => new Element(tag),
    querySelectorAll: selector => {
      if (selector === '[data-seat-id]:checked') {
        return elements.get('payment-seats').children.flatMap(label => label.children)
          .filter(node => node.checked && node.dataset.seatId);
      }
      return [];
    }
  };
  const context = {
    document, URLSearchParams, crypto: { randomUUID: (() => { let n = 0; return () => `uuid-${++n}`; })() },
    sessionStorage: { getItem: key => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) },
    location: { origin: 'http://localhost:8088', search },
    fetch: async (url, options = {}) => {
      calls.push({ url, ...options });
      const body = await fetchHandler(url, options);
      return { ok: true, status: 200, json: async () => ({ data: body }) };
    },
    TossPayments: key => { sdkCalls.push({ key }); return { payment: params => ({
      requestPayment: async request => { sdkCalls.push({ params, request }); }
    }) }; }
  };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(sourcePath, 'utf8'), context);
  return { elements, storage, calls, sdkCalls, context,
    async ready() { await new Promise(resolve => setImmediate(resolve)); },
    state() { return JSON.parse(storage.get('cgv-payment-demo')); } };
}

const order = { orderId: 'order_fixture', paymentId: 2, totalAmount: 24000,
  orderName: '테스트 좌석 2석', seats: [], expiresAt: '2030-01-01T00:00:00Z' };
const approved = { paymentId: 2, orderId: 'order_fixture', status: 'DONE',
  totalAmount: 24000, balanceAmount: 24000, seats: [
    { reservedSeatId: 11, seatRow: 'A', seatNumber: 1, canceledAt: null },
    { reservedSeatId: 12, seatRow: 'A', seatNumber: 2, canceledAt: null }
  ] };

test('redirect amount mismatch prevents approval', async () => {
  const app = boot({ screen: 'success', state: { token: 'test-token', order },
    search: '?orderId=order_fixture&paymentKey=payment_fixture&amount=1', fetchHandler: () => order });
  await app.ready();
  assert.equal(app.calls.filter(call => call.url === '/api/v1/payments/confirm').length, 0);
  assert.match(app.elements.get('notice').textContent, /금액/);
});

test('approval retry reuses the same idempotency key after an ambiguous response', async () => {
  let confirms = 0;
  const app = boot({ screen: 'success', state: { token: 'test-token', order },
    search: '?orderId=order_fixture&paymentKey=payment_fixture&amount=24000',
    fetchHandler: url => {
      if (url.includes('payment-orders')) return order;
      if (url.includes('/confirm')) { confirms++; throw new Error('network'); }
      return [];
    } });
  await app.ready();
  await app.elements.get('retry-request').fire();
  const requests = app.calls.filter(call => call.url === '/api/v1/payments/confirm');
  assert.equal(confirms, 2);
  assert.equal(requests[0].headers['Idempotency-Key'], requests[1].headers['Idempotency-Key']);
  assert.equal(app.elements.get('cancel-selected').disabled, true);
});

test('cancel retry preserves its body and key, then a new cancel gets a fresh key', async () => {
  let cancellationCount = 0;
  const app = boot({ state: { token: 'test-token', order, paymentId: 2 },
    fetchHandler: url => {
      if (url.includes('/cancellations')) {
        if (++cancellationCount === 1) throw new Error('network');
        return { operationId: 7, status: 'SUCCEEDED', paymentId: 2, result: approved };
      }
      if (url === '/api/v1/payments/2') return approved;
      return [];
    } });
  await app.ready();
  app.elements.get('cancel-reason').value = '테스트 부분 취소';
  app.elements.get('payment-seats').children[0].children[0].checked = true;
  await app.elements.get('cancel-selected').fire();
  assert.equal(app.elements.get('cancel-remaining').disabled, true);
  await app.elements.get('retry-request').fire();
  app.elements.get('payment-seats').children[0].children[0].checked = true;
  await app.elements.get('cancel-selected').fire();
  const requests = app.calls.filter(call => call.url.includes('/cancellations'));
  assert.equal(requests.length, 3);
  assert.equal(requests[0].headers['Idempotency-Key'], requests[1].headers['Idempotency-Key']);
  assert.equal(requests[0].body, requests[1].body);
  assert.notEqual(requests[1].headers['Idempotency-Key'], requests[2].headers['Idempotency-Key']);
});

test('client key is used only for SDK initialization', async () => {
  const clientKey = 'test_ck_sdk_fixture';
  const app = boot({ state: { token: 'test-token', order },
    fetchHandler: url => url === '/api/v1/payment-config' ? { clientKey, currency: 'KRW' } : [] });
  await app.ready();
  await app.elements.get('open-payment').fire();
  assert.equal(app.sdkCalls[0].key, clientKey);
  assert.equal(app.sdkCalls[1].request.method, 'CARD');
  assert.equal(app.sdkCalls[1].request.successUrl, 'http://localhost:8088/payment-success.html');
  assert.equal([...app.storage.values()].some(value => value.includes(clientKey)), false);
  assert.equal([...app.elements.values()].some(element => element.textContent.includes(clientKey)), false);
});

test('a processing operation blocks new holds and cancellations', async () => {
  const app = boot({ state: { token: 'test-token', operation: { operationId: 7, status: 'PROCESSING' } } });
  await app.ready();
  assert.equal(app.elements.get('hold-seats').disabled, true);
  assert.equal(app.elements.get('cancel-selected').disabled, true);
  assert.equal(app.elements.get('reconcile-operation').disabled, false);
});

test('failure text is rendered as text and never inserted as HTML', async () => {
  const app = boot({ screen: 'fail', search: '?code=FAILED&message=%3Cimg%20src=x%20onerror=alert(1)%3E' });
  await app.ready();
  assert.match(app.elements.get('notice').textContent, /<img/);
  assert.equal(app.elements.get('notice').innerHTML, undefined);
});

test('reload after a cancellation refreshes the payment instead of replaying approval', async () => {
  const app = boot({ screen: 'success', state: { token: 'test-token', order, paymentId: 2,
    confirmation: { orderId: order.orderId, paymentKey: 'fixture', amount: 24000, key: 'saved-key', complete: true },
    operation: { operationId: 7, status: 'SUCCEEDED', type: 'CANCEL' } },
    search: '?orderId=order_fixture&paymentKey=fixture&amount=24000',
    fetchHandler: url => url === '/api/v1/payments/2' ? { ...approved, status: 'CANCELED', balanceAmount: 0 } : order });
  await app.ready();
  assert.equal(app.calls.some(call => call.url.includes('/confirm')), false);
  assert.match(app.elements.get('payment-summary').textContent, /CANCELED/);
});

test('reload preserves a cancellation whose response has not arrived', async () => {
  const pendingCancel = { paymentId: 2, key: 'saved-cancel-key', body: { reservedSeatIds: [11], reason: '취소' } };
  const app = boot({ screen: 'success', state: { token: 'test-token', order, paymentId: 2, pendingCancel,
    confirmation: { orderId: order.orderId, paymentKey: 'fixture', amount: 24000, key: 'approval-key' },
    operation: { status: 'UNKNOWN' } },
    fetchHandler: () => approved });
  await app.ready();
  assert.equal(app.calls.some(call => call.url.includes('/confirm')), false);
  assert.deepEqual(app.state().pendingCancel, pendingCancel);
  assert.equal(app.elements.get('cancel-selected').disabled, true);
});

test('payment lookup discovers an active operation for recovery', async () => {
  const app = boot({ state: { token: 'test-token', paymentId: 2 },
    fetchHandler: url => url === '/api/v1/payments/2'
      ? { ...approved, status: 'PROCESSING', activeOperationId: 19 } : [] });
  await app.ready();
  assert.equal(app.state().operation.operationId, 19);
  assert.equal(app.elements.get('cancel-selected').disabled, true);
  assert.equal(app.elements.get('refresh-operation').disabled, false);
  assert.equal(app.elements.get('reconcile-operation').disabled, false);
});

test('compensation also blocks new payment actions', async () => {
  const app = boot({ state: { token: 'test-token', operation: { operationId: 7, status: 'COMPENSATING' } } });
  await app.ready();
  assert.equal(app.elements.get('hold-seats').disabled, true);
  assert.equal(app.elements.get('reconcile-operation').disabled, false);
});
