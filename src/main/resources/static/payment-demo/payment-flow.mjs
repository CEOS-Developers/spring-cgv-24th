export class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export async function requestApi(path, { token, method = "GET", body, key } = {}, fetcher = fetch) {
  const headers = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (key) headers["Idempotency-Key"] = key;
  const response = await fetcher(path, {
    method, headers, credentials: "omit", cache: "no-store",
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(data?.resultMsg || "요청을 처리하지 못했습니다. 잠시 후 다시 확인해주세요.",
      response.status, data?.divisionCode);
  }
  if (data === null) throw new Error("서버 응답을 확인하지 못했습니다.");
  return Object.hasOwn(data, "result") ? data.result : data;
}

function validateOrder(order) {
  if (!order || typeof order.orderId !== "string" || !/^[a-zA-Z0-9_=\-]{6,64}$/.test(order.orderId)
      || !Number.isSafeInteger(order.totalAmount) || order.totalAmount <= 0) {
    throw new Error("저장된 주문을 확인할 수 없습니다. 주문 내역을 확인해주세요.");
  }
}

export function buildPaymentRequest(order, origin) {
  validateOrder(order);
  return {
    method: "CARD",
    amount: { currency: "KRW", value: order.totalAmount },
    orderId: order.orderId,
    orderName: (order.orderName || "CGV 매점 주문").slice(0, 100),
    successUrl: new URL("/payment-demo/index.html?flow=success", origin).href,
    failUrl: new URL("/payment-demo/index.html?flow=fail", origin).href,
    windowTarget: "self",
  };
}

// 리다이렉트 파라미터는 신뢰하지 않는다. 최종 소유자·금액 검증은 기존 서버 승인 API가 담당한다.
export function captureAuthentication(order, search) {
  validateOrder(order);
  const parameters = new URLSearchParams(search);
  for (const field of ["flow", "orderId", "paymentKey", "amount"]) {
    if (parameters.getAll(field).length !== 1) throw new Error("결제 인증 결과가 올바르지 않습니다.");
  }
  const rawAmount = parameters.get("amount");
  const paymentKey = parameters.get("paymentKey");
  if (parameters.get("flow") !== "success" || parameters.get("orderId") !== order.orderId
      || !/^[0-9]+$/.test(rawAmount) || !Number.isSafeInteger(Number(rawAmount))
      || Number(rawAmount) !== order.totalAmount || !paymentKey?.trim() || paymentKey.length > 200) {
    throw new Error("결제 인증 결과가 원래 주문과 일치하지 않습니다. 결제 내역을 확인해주세요.");
  }
  const confirmation = { paymentKey, orderId: order.orderId, amount: order.totalAmount };
  if (order.confirmation && (order.confirmation.paymentKey !== paymentKey
      || order.confirmation.orderId !== confirmation.orderId || order.confirmation.amount !== confirmation.amount)) {
    throw new Error("이미 확인 중인 결제가 있습니다. 기존 결제 승인을 다시 확인해주세요.");
  }
  if (!order.idempotencyKey) throw new Error("원래 결제 요청 정보를 찾을 수 없습니다.");
  return { ...order, confirmation };
}

export async function confirmOrder(order, token, fetcher = fetch) {
  validateOrder(order);
  const confirmation = order.confirmation;
  if (!order.idempotencyKey || !confirmation || confirmation.orderId !== order.orderId
      || confirmation.amount !== order.totalAmount || !confirmation.paymentKey?.trim()) {
    throw new Error("결제 인증 후 승인을 요청해주세요.");
  }
  return requestApi("/api/me/payments/confirm", {
    token, method: "POST", key: order.idempotencyKey, body: confirmation,
  }, fetcher);
}
