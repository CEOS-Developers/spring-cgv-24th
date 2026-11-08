import test from "node:test";
import assert from "node:assert/strict";
import {
  ApiError, buildPaymentRequest, captureAuthentication, confirmOrder, requestApi,
} from "../../main/resources/static/payment-demo/payment-flow.mjs";

const order = {
  purchaseId: 1, orderId: "cgv_store_123456", totalAmount: 10000,
  orderName: "팝콘 외 1종", idempotencyKey: "saved-confirm-key",
};
const returned = "?flow=success&orderId=cgv_store_123456&amount=10000&paymentKey=pg-payment-key";
const ok = (data) => new Response(JSON.stringify({ result: data }), {
  status: 200, headers: { "Content-Type": "application/json" },
});

test("서버에서 받은 주문번호와 금액으로 동일 출처의 성공·실패 URL을 만든다", () => {
  const request = buildPaymentRequest(order, "http://localhost:8080");
  assert.equal(request.orderId, order.orderId);
  assert.deepEqual(request.amount, { currency: "KRW", value: 10000 });
  assert.equal(request.method, "CARD");
  assert.equal(request.windowTarget, "self");
  assert.equal(request.successUrl, "http://localhost:8080/payment-demo/index.html?flow=success");
  assert.equal(request.failUrl, "http://localhost:8080/payment-demo/index.html?flow=fail");
  assert.equal(Object.hasOwn(request, "secretKey"), false);
});

test("금액·주문번호 변조, 중복 쿼리, 비정상 인증 결과를 거절한다", () => {
  for (const search of [
    returned.replace("amount=10000", "amount=1"),
    returned.replace("cgv_store_123456", "cgv_store_other"),
    returned + "&amount=10000",
    returned + "&flow=success",
    returned.replace("pg-payment-key", ""),
    returned.replace("amount=10000", "amount=1e4"),
    returned.replace("flow=success", "flow=fail"),
    returned.replace("&paymentKey=pg-payment-key", ""),
  ]) assert.throws(() => captureAuthentication(order, search));
  assert.throws(() => captureAuthentication(null, returned));
});

test("인증 결과와 멱등키를 저장한 뒤 새로고침해도 같은 요청을 복원한다", () => {
  const captured = captureAuthentication(order, returned);
  const restored = JSON.parse(JSON.stringify(captured));
  assert.deepEqual(captureAuthentication(restored, returned), captured);
  assert.equal(restored.idempotencyKey, order.idempotencyKey);
  assert.deepEqual(restored.confirmation, {
    paymentKey: "pg-payment-key", orderId: order.orderId, amount: order.totalAmount,
  });
  assert.throws(() => captureAuthentication(restored, returned.replace("pg-payment-key", "another-key")));
});

test("승인 응답 유실 뒤에도 같은 요청 본문과 멱등키를 전송한다", async () => {
  const saved = captureAuthentication(order, returned);
  const requests = [];
  const fetcher = async (url, options) => {
    requests.push({ url, ...options });
    if (requests.length === 1) throw new TypeError("connection reset after approval");
    return ok({ paymentId: 1, paymentStatus: "DONE" });
  };
  await assert.rejects(confirmOrder(saved, "access-token", fetcher));
  const result = await confirmOrder(JSON.parse(JSON.stringify(saved)), "access-token", fetcher);
  assert.equal(result.paymentStatus, "DONE");
  assert.deepEqual(requests[0], requests[1]);
  assert.equal(requests[1].url, "/api/me/payments/confirm");
  assert.equal(requests[1].headers["Idempotency-Key"], "saved-confirm-key");
  assert.equal(requests[1].headers.Authorization, "Bearer access-token");
  assert.deepEqual(JSON.parse(requests[1].body), saved.confirmation);
});

test("로그인 만료 후 같은 계정으로 다시 로그인해도 원래 승인 요청을 유지한다", async () => {
  const saved = captureAuthentication(order, returned);
  const requests = [];
  const fetcher = async (url, options) => {
    requests.push(options);
    if (requests.length === 1) return new Response(JSON.stringify({ resultMsg: "토큰 만료", divisionCode: "AUTH002" }), { status: 401 });
    return ok({ paymentStatus: "DONE" });
  };
  await assert.rejects(confirmOrder(saved, "expired-token", fetcher), (error) => error instanceof ApiError && error.status === 401);
  await confirmOrder(saved, "new-token", fetcher);
  assert.equal(requests[0].body, requests[1].body);
  assert.equal(requests[0].headers["Idempotency-Key"], requests[1].headers["Idempotency-Key"]);
  assert.equal(requests[1].headers.Authorization, "Bearer new-token");
});

test("결과 불명은 성공으로 바꾸지 않고 서버 오류 코드를 보존한다", async () => {
  await assert.rejects(confirmOrder(captureAuthentication(order, returned), "token", async () =>
    new Response(JSON.stringify({ resultMsg: "결과 불명", divisionCode: "PAY009" }), { status: 502 })),
  (error) => error instanceof ApiError && error.code === "PAY009" && error.status === 502);
});

test("인증 전이나 저장된 금액이 다른 요청은 PG 승인 API를 호출하지 않는다", async () => {
  let calls = 0;
  const fetcher = async () => { calls++; return ok({}); };
  await assert.rejects(confirmOrder(order, "token", fetcher));
  const captured = captureAuthentication(order, returned);
  await assert.rejects(confirmOrder({ ...captured, totalAmount: 1 }, "token", fetcher));
  await assert.rejects(confirmOrder({ ...captured, idempotencyKey: "" }, "token", fetcher));
  assert.equal(calls, 0);
});

test("로그인 응답과 공통 API 응답을 읽고 브라우저 범위를 넘는 금액은 거절한다", async () => {
  assert.deepEqual(await requestApi("/login", {}, async () => new Response(JSON.stringify({ accessToken: "token" }))), { accessToken: "token" });
  assert.throws(() => buildPaymentRequest({ ...order, totalAmount: Number.MAX_SAFE_INTEGER + 1 }, "http://localhost"));
  assert.throws(() => buildPaymentRequest({ totalAmount: 1 }, "http://localhost"));
});
