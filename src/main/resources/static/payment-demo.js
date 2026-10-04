(() => {
  'use strict';

  const storageKey = 'cgv-payment-demo';
  const element = id => document.getElementById(id);
  const screen = document.body.dataset.screen;
  let session = readSession();
  let payment = null;
  let busy = false;

  function readSession() {
    try { return JSON.parse(sessionStorage.getItem(storageKey)) || {}; }
    catch { return {}; }
  }

  function save() { sessionStorage.setItem(storageKey, JSON.stringify(session)); }
  function notice(message) { if (element('notice')) element('notice').textContent = message; }
  function show(id, text) { if (element(id)) element(id).textContent = text; }
  function value(id) { return element(id)?.value.trim() || ''; }
  function money(amount) { return Number(amount || 0).toLocaleString('ko-KR') + '원'; }
  function unresolved() {
    return Boolean(session.pendingCancel) || ['PROCESSING', 'UNKNOWN', 'COMPENSATING'].includes(session.operation?.status)
      || ['PROCESSING', 'COMPENSATING'].includes(payment?.status);
  }

  function disable(id, disabled) { if (element(id)) element(id).disabled = disabled; }
  function syncButtons() {
    const locked = busy || unresolved();
    const loggedIn = Boolean(session.token);
    disable('hold-seats', locked || !loggedIn);
    disable('create-order', locked || !loggedIn || !session.hold || Boolean(session.order));
    disable('release-hold', locked || !loggedIn || !session.hold);
    disable('open-payment', locked || !loggedIn || !session.order || Boolean(payment));
    const cancelable = payment && ['DONE', 'PARTIALLY_CANCELED'].includes(payment.status)
      && Number(payment.balanceAmount) > 0;
    disable('cancel-selected', locked || !loggedIn || !cancelable);
    disable('cancel-remaining', locked || !loggedIn || !cancelable);
    disable('refresh-operation', busy || !loggedIn || !session.operation?.operationId);
    disable('reconcile-operation', busy || !loggedIn || !session.operation?.operationId || !unresolved());
    disable('retry-request', busy || !loggedIn || Boolean(session.operation?.operationId)
      || !(session.pendingCancel || session.confirmation));
    disable('retry-confirm', busy || !loggedIn || !session.confirmation || unresolved());
    disable('load-payment', busy || !loggedIn);
    disable('logout', busy || unresolved());
  }

  async function action(callback) {
    if (busy) return;
    busy = true;
    syncButtons();
    try { await callback(); }
    catch (error) {
      notice(error.userMessage || '요청 결과를 확인하지 못했습니다. 같은 요청 다시 확인 또는 처리 상태 조회를 이용하세요.');
    } finally { busy = false; renderOperation(); syncButtons(); }
  }

  function on(id, event, callback) {
    element(id)?.addEventListener(event, async e => { e.preventDefault(); await action(callback); });
  }

  function userError(message) { const error = new Error(message); error.userMessage = message; return error; }

  async function api(path, method = 'GET', body, requestKey) {
    const headers = { Accept: 'application/json' };
    if (session.token) headers.Authorization = 'Bearer ' + session.token;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (requestKey) headers['Idempotency-Key'] = requestKey;
    const response = await fetch(path, { method, headers, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
    if (response.status === 204) return null;
    let payload;
    try { payload = await response.json(); }
    catch { throw userError('서버 응답을 읽지 못했습니다. 처리 상태를 조회해주세요.'); }
    if (!response.ok) {
      const error = userError(response.status === 401 && path !== '/api/v1/auth/login' ? '로그인이 만료되었습니다. 데모 화면에서 다시 로그인해주세요.'
        : payload.message || '요청을 처리할 수 없습니다.');
      error.httpStatus = response.status;
      throw error;
    }
    return payload.data;
  }

  function renderOrder() {
    show('auth-state', session.token ? (session.loginId || '회원') + ' 로그인됨' : '로그인이 필요합니다.');
    if (!session.order) {
      show('order-summary', session.hold ? '좌석을 선점했습니다. 주문을 생성해주세요.' : '좌석 선점 → 주문 생성 → 테스트 결제창 순서로 진행하세요.');
      return;
    }
    show('order-summary', `${session.order.orderName}\n주문 ${session.order.orderId}\n결제 금액 ${money(session.order.totalAmount)}\n선점 만료 ${session.order.expiresAt}`);
  }

  function renderPayment(result) {
    payment = result;
    session.paymentId = result.paymentId;
    if (result.activeOperationId) {
      session.operation = { operationId: result.activeOperationId,
        status: session.operation?.operationId === result.activeOperationId ? session.operation.status : 'UNKNOWN' };
    }
    if (element('payment-id')) element('payment-id').value = result.paymentId;
    show('payment-summary', `결제 번호 ${result.paymentId}\n주문 ${result.orderId}\n상태 ${result.status}\n원금 ${money(result.totalAmount)} / 잔액 ${money(result.balanceAmount)}`);
    const seats = element('payment-seats');
    if (seats) {
      seats.replaceChildren();
      for (const seat of result.seats || []) {
        const label = document.createElement('label');
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.dataset.seatId = String(seat.reservedSeatId);
        checkbox.disabled = Boolean(seat.canceledAt);
        const text = document.createElement('span');
        text.textContent = `${seat.seatRow}${seat.seatNumber} · ${seat.canceledAt ? '취소됨' : '예매 유지'}`;
        label.append(checkbox, text);
        seats.append(label);
      }
    }
    save();
  }

  function renderOperation() {
    const operation = session.operation;
    if (!operation) { show('operation-summary', '진행 중인 결제 요청이 없습니다.'); return; }
    const labels = { PROCESSING: '처리 중', UNKNOWN: '결과 확인 필요', COMPENSATING: '승인 결과 정리 중', SUCCEEDED: '완료', FAILED: '실패' };
    show('operation-summary', `요청 ${operation.operationId || '응답 대기'} · ${labels[operation.status] || operation.status}`
      + (unresolved() ? '\n새 결제나 취소를 시작하기 전에 조회 또는 복구로 결과를 확인해주세요.' : '')
      + (operation.failureCode ? `\n실패 코드 ${operation.failureCode}` : ''));
  }

  async function applyOperation(operation) {
    session.operation = { operationId: operation.operationId, status: operation.status,
      type: operation.type, failureCode: operation.failureCode };
    if (operation.paymentId) session.paymentId = operation.paymentId;
    if (['SUCCEEDED', 'FAILED'].includes(operation.status)) {
      delete session.pendingCancel;
      if (operation.status === 'SUCCEEDED') { delete session.hold; delete session.holdRequest; }
    }
    if (operation.type === 'CONFIRM' && operation.status === 'SUCCEEDED' && session.confirmation) {
      session.confirmation.complete = true;
    }
    if (operation.type === 'CONFIRM' && operation.status === 'FAILED') {
      delete session.hold;
      delete session.holdRequest;
      delete session.order;
      delete session.confirmation;
    }
    save();
    // 작업의 저장된 결과보다 현재 결제 상태와 잔액을 취소 화면의 기준으로 사용한다.
    if (session.paymentId) renderPayment(await api(`/api/v1/payments/${session.paymentId}`));
    notice(operation.status === 'SUCCEEDED' ? '요청이 완료되었습니다.' : operation.status === 'FAILED'
      ? '요청이 실패했습니다. 결제 상태와 실패 코드를 확인해주세요.' : '처리 결과가 아직 확정되지 않았습니다. 조회 또는 복구로 확인해주세요.');
    renderOrder();
  }

  async function confirm() {
    const confirmation = session.confirmation;
    if (!confirmation) throw userError('결제 인증 결과가 없습니다. 주문 화면에서 시작해주세요.');
    const order = await api(`/api/v1/payment-orders/${encodeURIComponent(confirmation.orderId)}`);
    if (Number(order.totalAmount) !== confirmation.amount
      || (session.order?.orderId === confirmation.orderId && Number(session.order.totalAmount) !== confirmation.amount)) {
      throw userError('결제 인증 금액이 주문 금액과 다릅니다. 승인을 진행하지 않았습니다.');
    }
    session.order = order;
    session.operation = { status: 'UNKNOWN' };
    save();
    try {
      const operation = await api('/api/v1/payments/confirm', 'POST', {
        paymentKey: confirmation.paymentKey, orderId: confirmation.orderId, amount: confirmation.amount
      }, confirmation.key);
      if (operation.status === 'SUCCEEDED') confirmation.complete = true;
      await applyOperation(operation);
    } catch (error) {
      if (error.httpStatus && error.httpStatus < 500) { delete session.operation; save(); }
      throw error;
    }
  }

  async function cancel(body) {
    if (unresolved()) throw userError('진행 중인 요청 결과를 먼저 확인해주세요.');
    session.pendingCancel = { paymentId: payment.paymentId, body, key: crypto.randomUUID() };
    save();
    await retryCancel();
  }

  async function retryCancel() {
    const pending = session.pendingCancel;
    session.operation = { status: 'UNKNOWN' };
    save();
    try {
      await applyOperation(await api(`/api/v1/payments/${pending.paymentId}/cancellations`, 'POST', pending.body, pending.key));
    } catch (error) {
      if (error.httpStatus && error.httpStatus < 500) {
        delete session.pendingCancel; delete session.operation; save();
      }
      throw error;
    }
  }

  on('login-form', 'submit', async () => {
    const loginId = value('login-id');
    const result = await api('/api/v1/auth/login', 'POST', { loginId, password: element('password').value });
    session.token = result.accessToken;
    session.loginId = loginId;
    session.customerKey ||= crypto.randomUUID();
    element('password').value = '';
    save(); renderOrder(); notice('로그인했습니다.');
    if (session.paymentId) renderPayment(await api(`/api/v1/payments/${session.paymentId}`));
  });

  on('signup-form', 'submit', async () => {
    await api('/api/v1/auth/signup', 'POST', { loginId: value('signup-id'), name: value('signup-name'),
      email: value('signup-email'), password: element('signup-password').value });
    element('signup-password').value = '';
    notice('가입했습니다. 위 로그인 영역에서 로그인해주세요.');
  });

  on('logout', 'click', async () => {
    if (unresolved()) throw userError('처리 결과를 먼저 확인해주세요.');
    session = {}; payment = null; sessionStorage.removeItem(storageKey);
    show('payment-summary', '결제 번호를 입력하면 상태를 조회할 수 있습니다.');
    element('payment-seats')?.replaceChildren(); renderOrder(); notice('데모 로그인 정보를 지웠습니다.');
  });

  on('hold-seats', 'click', async () => {
    if (unresolved()) throw userError('진행 중인 결제 결과를 먼저 확인해주세요.');
    const screeningId = Number(value('screening-id'));
    if (!Number.isSafeInteger(screeningId) || screeningId <= 0) throw userError('상영 번호를 확인해주세요.');
    const coordinates = value('seats').toUpperCase().split(/[,\s]+/).filter(Boolean);
    if (!coordinates.length || coordinates.some(coordinate => !/^[A-Z][1-9]\d*$/.test(coordinate))) {
      throw userError('좌석은 A1,A2 형태로 입력해주세요.');
    }
    const body = { screeningId, seats: coordinates.map(coordinate => ({
      seatRow: coordinate[0], seatNumber: Number(coordinate.slice(1)) })) };
    const signature = JSON.stringify(body);
    if (session.holdRequest?.signature !== signature) session.holdRequest = { signature, key: crypto.randomUUID() };
    save();
    session.hold = await api('/api/v1/seat-holds', 'POST', body, session.holdRequest.key);
    delete session.order; delete session.operation; delete session.confirmation; delete session.paymentId;
    payment = null;
    show('payment-summary', '새 주문의 결제 완료 후 상태를 조회할 수 있습니다.');
    element('payment-seats')?.replaceChildren();
    save(); renderOrder(); notice('좌석을 선점했습니다. 주문을 생성해주세요.');
  });

  on('create-order', 'click', async () => {
    session.order = await api('/api/v1/payment-orders', 'POST', { reservationId: session.hold.reservationId });
    save(); renderOrder(); notice('주문을 생성했습니다. 테스트 결제창을 열어주세요.');
  });

  on('release-hold', 'click', async () => {
    if (unresolved()) throw userError('진행 중인 결제 결과를 먼저 확인해주세요.');
    await api(`/api/v1/seat-holds/${session.hold.reservationId}`, 'DELETE');
    delete session.hold; delete session.holdRequest; delete session.order; delete session.confirmation;
    save(); renderOrder(); notice('좌석 선점을 해제했습니다.');
  });

  on('open-payment', 'click', async () => {
    if (unresolved()) throw userError('진행 중인 요청 결과를 먼저 확인해주세요.');
    if (typeof window.TossPayments !== 'function') throw userError('결제 SDK를 불러오지 못했습니다. 새로고침해주세요.');
    const config = await api('/api/v1/payment-config');
    session.customerKey ||= crypto.randomUUID();
    save();
    // 키는 저장하거나 표시하지 않고 SDK 초기화에만 전달한다.
    const checkout = window.TossPayments(config.clientKey).payment({ customerKey: session.customerKey });
    try {
      await checkout.requestPayment({ method: 'CARD',
        amount: { currency: config.currency, value: Number(session.order.totalAmount) },
        orderId: session.order.orderId, orderName: session.order.orderName,
        successUrl: location.origin + '/payment-success.html', failUrl: location.origin + '/payment-fail.html',
        card: { flowMode: 'DEFAULT' } });
    } catch { throw userError('결제창이 종료되었거나 요청하지 못했습니다. 주문과 선점 만료를 확인해주세요.'); }
  });

  on('load-payment', 'click', async () => {
    const paymentId = Number(value('payment-id'));
    if (!Number.isSafeInteger(paymentId) || paymentId <= 0) throw userError('결제 번호를 확인해주세요.');
    renderPayment(await api(`/api/v1/payments/${paymentId}`)); notice('결제 상태를 조회했습니다.');
  });

  on('cancel-selected', 'click', async () => {
    const reservedSeatIds = [...document.querySelectorAll('[data-seat-id]:checked')].map(checkbox => Number(checkbox.dataset.seatId));
    if (!reservedSeatIds.length) throw userError('취소할 좌석을 선택해주세요.');
    const reason = value('cancel-reason');
    if (!reason) throw userError('취소 사유를 입력해주세요.');
    await cancel({ reservedSeatIds, reason });
  });
  on('cancel-remaining', 'click', async () => {
    const reason = value('cancel-reason');
    if (!reason) throw userError('취소 사유를 입력해주세요.');
    await cancel({ reservedSeatIds: null, reason });
  });
  on('refresh-operation', 'click', async () => applyOperation(await api(`/api/v1/payment-operations/${session.operation.operationId}`)));
  on('reconcile-operation', 'click', async () => applyOperation(await api(`/api/v1/payment-operations/${session.operation.operationId}/reconcile`, 'POST')));
  on('retry-request', 'click', async () => session.pendingCancel ? retryCancel() : confirm());
  on('retry-confirm', 'click', confirm);

  async function initialize() {
    renderOrder(); renderOperation(); syncButtons();
    if (screen === 'fail') {
      const params = new URLSearchParams(location.search);
      notice(`결제 인증이 완료되지 않았습니다. ${params.get('code') || ''} ${params.get('message') || ''}`);
      return;
    }
    if (screen === 'success') {
      const params = new URLSearchParams(location.search);
      if (params.has('paymentKey') || params.has('orderId') || params.has('amount')) {
        const amount = Number(params.get('amount'));
        const orderId = params.get('orderId');
        const paymentKey = params.get('paymentKey');
        if (!orderId || !paymentKey || !Number.isSafeInteger(amount) || amount <= 0) {
          throw userError('결제 인증 결과를 확인할 수 없습니다. 주문 화면에서 다시 진행해주세요.');
        }
        const previous = session.confirmation;
        session.approvalKeys ||= {};
        session.approvalKeys[orderId] ||= previous?.orderId === orderId ? previous.key : crypto.randomUUID();
        session.confirmation = { orderId, paymentKey, amount, key: session.approvalKeys[orderId],
          complete: previous?.orderId === orderId && previous.complete === true };
        save();
      }
      if (!session.token) throw userError('데모 화면에서 로그인한 뒤 이 결과 화면을 새로고침해주세요.');
      if (session.operation?.operationId && unresolved()) {
        await applyOperation(await api(`/api/v1/payment-operations/${session.operation.operationId}`));
      } else if (session.pendingCancel || session.confirmation?.complete) {
        if (session.paymentId) renderPayment(await api(`/api/v1/payments/${session.paymentId}`));
        notice(session.pendingCancel ? '취소 결과를 확인해야 합니다. 같은 요청 다시 확인을 이용하세요.' : '최신 결제 상태를 조회했습니다.');
      } else { await confirm(); }
      return;
    }
    const movies = await api('/api/v1/movies');
    const movie = movies.find(candidate => candidate.title === '결제 테스트 상영') || movies[0];
    if (movie) {
      const screenings = await api(`/api/v1/movies/${movie.movieId}/screenings`);
      const screening = screenings.find(candidate => new Date(candidate.startAt).getTime() > Date.now());
      if (screening) {
        element('screening-id').value = screening.screeningId;
        show('screening-description', `${movie.title} · 상영 ${screening.screeningId} · ${screening.startAt}`);
      }
    }
    if (session.paymentId && session.token) renderPayment(await api(`/api/v1/payments/${session.paymentId}`));
    syncButtons();
  }

  action(initialize);
})();
