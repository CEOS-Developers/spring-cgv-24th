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
      if (body?.$httpStatus) {
        return { ok: body.$httpStatus < 400, status: body.$httpStatus, json: async () => {
          if (body.$invalidJson) throw new SyntaxError('invalid JSON');
          return body.payload;
        } };
      }
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

test('failed approval clears the completed hold so the same seats get a fresh hold key', async () => {
  const holdBody = { screeningId: 3, seats: [
    { seatRow: 'A', seatNumber: 1 }, { seatRow: 'A', seatNumber: 2 }
  ] };
  const app = boot({ state: { token: 'test-token', order, hold: { reservationId: 4 }, paymentId: 2,
    holdRequest: { signature: JSON.stringify(holdBody), key: 'old-hold-key' },
    confirmation: { orderId: order.orderId, paymentKey: 'fixture', amount: 24000, key: 'approval-key' },
    operation: { operationId: 7, type: 'CONFIRM', status: 'UNKNOWN' } },
    fetchHandler: url => {
      if (url === '/api/v1/payment-operations/7') return { operationId: 7, type: 'CONFIRM', status: 'FAILED', paymentId: 2 };
      if (url === '/api/v1/payments/2') return { ...approved, status: 'FAILED' };
      if (url === '/api/v1/seat-holds') return { reservationId: 8 };
      return [];
    } });
  await app.ready();
  await app.elements.get('refresh-operation').fire();
  assert.equal(app.state().hold, undefined);
  assert.equal(app.state().holdRequest, undefined);
  assert.equal(app.state().order, undefined);
  assert.equal(app.state().confirmation, undefined);
  assert.equal(app.state().paymentId, 2);
  assert.equal(app.state().operation.operationId, 7);
  app.elements.get('screening-id').value = '3';
  app.elements.get('seats').value = 'A1,A2';
  await app.elements.get('hold-seats').fire();
  const request = app.calls.find(call => call.url === '/api/v1/seat-holds');
  assert.ok(request);
  assert.notEqual(request.headers['Idempotency-Key'], 'old-hold-key');
  assert.equal(app.state().hold.reservationId, 8);
});

const holdBody = { screeningId: 3, seats: [
  { seatRow: 'A', seatNumber: 1 }, { seatRow: 'A', seatNumber: 2 }
] };
const heldSession = { token: 'owner-a-token', loginId: 'owner-a', customerKey: 'owner-a-customer',
  hold: { reservationId: 4 }, holdRequest: { signature: JSON.stringify(holdBody), key: 'old-hold-key' },
  order, paymentId: 2, operation: { operationId: 7, status: 'SUCCEEDED' },
  confirmation: { orderId: order.orderId, paymentKey: 'fixture', amount: 24000, key: 'approval-key', complete: true },
  approvalKeys: { order_fixture: 'approval-key' } };
const httpError = (status, code) => ({ $httpStatus: status, payload: { code, message: code } });

function selectHeldSeats(app) {
  app.elements.get('screening-id').value = '3';
  app.elements.get('seats').value = 'A1,A2';
}

function assertCheckoutCleared(app) {
  const state = app.state();
  for (const key of ['hold', 'holdRequest', 'order', 'confirmation', 'paymentId', 'operation', 'approvalKeys']) {
    assert.equal(state[key], undefined, `${key} should be cleared`);
  }
  assert.equal(app.elements.get('payment-id').value, '');
  assert.equal(app.elements.get('payment-seats').children.length, 0);
  assert.equal(app.elements.get('hold-seats').disabled, false);
  for (const id of ['create-order', 'release-hold', 'open-payment', 'cancel-selected', 'cancel-remaining']) {
    assert.equal(app.elements.get(id).disabled, true, `${id} should be disabled`);
  }
  assert.doesNotMatch(app.elements.get('order-summary').textContent, /order_fixture/);
  assert.doesNotMatch(app.elements.get('payment-summary').textContent, /결제 번호 2/);
  assert.doesNotMatch(app.elements.get('operation-summary').textContent, /요청 7/);
}

for (const code of ['HOLD_EXPIRED', 'HOLD_NOT_ACTIVE']) {
  test(`${code} from hold creation clears stale checkout and lets the next same-seat attempt use a fresh key`, async () => {
    let holdAttempts = 0;
    const fetchHandler = url => {
      if (url === '/api/v1/seat-holds') return ++holdAttempts === 1 ? httpError(409, code) : { reservationId: 8 };
      return url === '/api/v1/payments/2' ? approved : [];
    };
    const app = boot({ state: heldSession, fetchHandler });
    await app.ready();
    selectHeldSeats(app);
    await app.elements.get('hold-seats').fire();
    assert.equal(holdAttempts, 1, 'a terminal response must not silently retry the POST');
    assertCheckoutCleared(app);
    assert.equal(app.state().token, 'owner-a-token');
    assert.equal(app.state().customerKey, 'owner-a-customer');
    assert.match(app.elements.get('notice').textContent, new RegExp(code));

    const reloaded = boot({ state: app.state(), fetchHandler });
    await reloaded.ready();
    selectHeldSeats(reloaded);
    await reloaded.elements.get('hold-seats').fire();
    const request = reloaded.calls.find(call => call.url === '/api/v1/seat-holds');
    assert.notEqual(request.headers['Idempotency-Key'], 'old-hold-key');
    assert.deepEqual(JSON.parse(request.body), holdBody);
    assert.equal(reloaded.state().hold.reservationId, 8);
  });

  test(`${code} from hold release clears stale checkout and permits a fresh same-seat hold`, async () => {
    const app = boot({ state: heldSession, fetchHandler: url => {
      if (url === '/api/v1/seat-holds/4') return httpError(409, code);
      if (url === '/api/v1/seat-holds') return { reservationId: 8 };
      return url === '/api/v1/payments/2' ? approved : [];
    } });
    await app.ready();
    await app.elements.get('release-hold').fire();
    assertCheckoutCleared(app);
    assert.equal(app.calls.filter(call => call.url === '/api/v1/seat-holds/4').length, 1);
    assert.equal(app.calls.some(call => call.url === '/api/v1/seat-holds'), false);
    selectHeldSeats(app);
    await app.elements.get('hold-seats').fire();
    const request = app.calls.find(call => call.url === '/api/v1/seat-holds');
    assert.notEqual(request.headers['Idempotency-Key'], 'old-hold-key');
    assert.equal(app.state().hold.reservationId, 8);
  });
}

const holdFailures = [
  ['network failure', () => { throw new Error('network'); }],
  ['server failure carrying a hold code', () => httpError(503, 'HOLD_EXPIRED')],
  ['unreadable response', () => ({ $httpStatus: 409, $invalidJson: true })],
  ['other business rejection', () => httpError(409, 'SEAT_ALREADY_RESERVED')],
  ['payment still processing', () => httpError(409, 'PAYMENT_ALREADY_PROCESSING')]
];
for (const [failureName, fail] of holdFailures) {
  for (const action of ['hold-seats', 'release-hold']) {
    test(`${action} preserves checkout and the hold key after ${failureName}`, async () => {
      let attempts = 0;
      const requestPath = action === 'hold-seats' ? '/api/v1/seat-holds' : '/api/v1/seat-holds/4';
      const app = boot({ state: heldSession, fetchHandler: url => {
        if (url === requestPath) { attempts++; return fail(); }
        return url === '/api/v1/payments/2' ? approved : [];
      } });
      await app.ready();
      const before = app.state();
      selectHeldSeats(app);
      await app.elements.get(action).fire();
      assert.deepEqual(app.state(), before);
      assert.equal(attempts, 1);
      await app.elements.get(action).fire();
      assert.deepEqual(app.state(), before);
      assert.equal(attempts, 2);
      if (action === 'hold-seats') {
        assert.deepEqual(app.calls.filter(call => call.url === requestPath)
          .map(call => call.headers['Idempotency-Key']), ['old-hold-key', 'old-hold-key']);
      }
    });
  }
}

test('same-account reauthentication preserves an unresolved cancellation and renews its recovery token', async () => {
  const pendingCancel = { paymentId: 2, key: 'pending-cancel-key', body: { reservedSeatIds: [11], reason: '취소' } };
  const app = boot({ state: { ...heldSession, operation: { operationId: 7, status: 'UNKNOWN' }, pendingCancel },
    fetchHandler: url => {
      if (url === '/api/v1/auth/login') return { accessToken: 'owner-a-new-token' };
      if (url === '/api/v1/payment-operations/7') return { operationId: 7, status: 'UNKNOWN', paymentId: 2 };
      return url === '/api/v1/payments/2' ? { ...approved, activeOperationId: 7 } : [];
    } });
  await app.ready();
  const before = app.state();
  app.elements.get('login-id').value = 'owner-a';
  app.elements.get('password').value = 'fixture-password';
  await app.elements.get('login-form').fire('submit');
  assert.deepEqual(app.state(), { ...before, token: 'owner-a-new-token' });
  assert.equal(app.elements.get('password').value, '');
  assert.equal(app.elements.get('hold-seats').disabled, true);
  assert.equal(app.elements.get('logout').disabled, true);
  await app.elements.get('refresh-operation').fire();
  const request = app.calls.find(call => call.url === '/api/v1/payment-operations/7');
  assert.equal(request.headers.Authorization, 'Bearer owner-a-new-token');
  assert.deepEqual(app.state().pendingCancel, pendingCancel);
  assert.equal(app.state().operation.operationId, 7);
});

for (const pending of [
  { name: 'unknown operation', operation: { operationId: 7, status: 'UNKNOWN' } },
  { name: 'pending cancellation', operation: undefined,
    pendingCancel: { paymentId: 2, key: 'pending-cancel-key', body: { reservedSeatIds: [11], reason: '취소' } } },
  { name: 'processing payment', operation: undefined, paymentStatus: 'PROCESSING' }
]) {
  test(`another-account login is rejected before authentication while ${pending.name} is unresolved`, async () => {
    const app = boot({ state: { ...heldSession, operation: pending.operation, pendingCancel: pending.pendingCancel },
      fetchHandler: url => {
        if (url === '/api/v1/auth/login') return { accessToken: 'owner-b-token' };
        return url === '/api/v1/payments/2' ? { ...approved, status: pending.paymentStatus || 'DONE' } : [];
      } });
    await app.ready();
    const before = app.state();
    app.elements.get('login-id').value = 'owner-b';
    app.elements.get('password').value = 'fixture-password';
    await app.elements.get('login-form').fire('submit');
    assert.equal(app.calls.some(call => call.url === '/api/v1/auth/login'), false);
    assert.deepEqual(app.state(), before);
    assert.match(app.elements.get('auth-state').textContent, /owner-a/);
    assert.match(app.elements.get('notice').textContent, /다른 계정/);
    assert.equal(app.elements.get('hold-seats').disabled, true);
    assert.equal(app.elements.get('logout').disabled, true);
  });
}

for (const status of [undefined, 'SUCCEEDED', 'FAILED']) {
  test(`successful account change clears the previous account checkout after ${status || 'ordinary state'}`, async () => {
    const app = boot({ state: { ...heldSession, operation: status ? { operationId: 7, status } : undefined },
      fetchHandler: url => {
        if (url === '/api/v1/auth/login') return { accessToken: 'owner-b-token' };
        return url === '/api/v1/payments/2' ? approved : [];
      } });
    await app.ready();
    app.elements.get('login-id').value = 'owner-b';
    app.elements.get('password').value = 'fixture-password';
    await app.elements.get('login-form').fire('submit');
    assertCheckoutCleared(app);
    assert.equal(app.state().token, 'owner-b-token');
    assert.equal(app.state().loginId, 'owner-b');
    assert.notEqual(app.state().customerKey, 'owner-a-customer');
    assert.equal(app.elements.get('logout').disabled, false);
    assert.match(app.elements.get('auth-state').textContent, /owner-b/);
    assert.equal(app.calls.some(call => call.url === '/api/v1/payments/2'
      && call.headers.Authorization === 'Bearer owner-b-token'), false);
  });
}

test('failed account change keeps the previous account session and rendered checkout', async () => {
  const app = boot({ state: heldSession, fetchHandler: url => {
    if (url === '/api/v1/auth/login') return httpError(401, 'LOGIN_FAILED');
    return url === '/api/v1/payments/2' ? approved : [];
  } });
  await app.ready();
  const before = app.state();
  const summaries = ['auth-state', 'order-summary', 'payment-summary', 'operation-summary'];
  const beforeText = summaries.map(id => app.elements.get(id).textContent);
  app.elements.get('login-id').value = 'owner-b';
  app.elements.get('password').value = 'wrong-password';
  await app.elements.get('login-form').fire('submit');
  assert.deepEqual(app.state(), before);
  assert.deepEqual(summaries.map(id => app.elements.get(id).textContent), beforeText);
  assert.equal(app.elements.get('payment-seats').children.length, 2);
  assert.match(app.elements.get('notice').textContent, /LOGIN_FAILED/);
});
