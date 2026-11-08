import { ApiError, requestApi, buildPaymentRequest, captureAuthentication, confirmOrder } from "./payment-flow.mjs";

const AUTH_STORAGE = "cgv-payment-demo-auth";
const ORDER_STORAGE = "cgv-payment-demo-order";
const element = (id) => document.getElementById(id);
const won = (amount) => `${amount.toLocaleString("ko-KR")}원`;
let auth = null;
let order = null;
let config = null;
let products = [];
let busy = true;
let sdkLoading;

function message(text, kind = "") {
  element("message").textContent = text;
  element("message").className = `message ${kind}`;
}

function saveOrder(next) {
  // PG 요청 전에 반드시 저장한다. 새로고침·응답 유실에도 원래 요청과 멱등키를 유지한다.
  if (next) sessionStorage.setItem(ORDER_STORAGE, JSON.stringify(next));
  else sessionStorage.removeItem(ORDER_STORAGE);
  order = next;
}

function render() {
  const locked = Boolean(order);
  element("login-button").disabled = busy;
  element("login-status").textContent = auth ? `${auth.username}님으로 로그인했습니다.`
    : "계정이 없다면 Swagger의 회원가입 API에서 먼저 가입해주세요.";
  element("cinema").disabled = busy || locked;
  element("order-button").disabled = busy || locked || !auth || !config || !products.length;
  for (const input of document.querySelectorAll("[data-product-id]")) input.disabled = busy || locked;
  element("empty-order").hidden = locked;
  element("order-info").hidden = !locked;
  element("pay-button").hidden = !order || Boolean(order.confirmation) || order.terminal;
  element("pay-button").disabled = busy || !auth || !config;
  element("confirm-button").hidden = !order?.confirmation || order.terminal;
  element("confirm-button").disabled = busy || !auth;
  element("lookup-button").hidden = !order?.receipt;
  element("lookup-button").disabled = busy || !auth;
  element("new-order-button").hidden = !order?.terminal;
  element("new-order-button").disabled = busy;
  if (!order) return;
  element("order-name").textContent = order.orderName;
  element("order-id").textContent = order.orderId;
  element("order-amount").textContent = won(order.totalAmount);
  element("order-expiry").textContent = `주문 유효시간: ${order.expiresAt.replace("T", " ")}`;
  element("order-state").textContent = order.receipt ? "결제가 승인되었습니다."
    : order.terminal ? `주문 상태: ${order.status}`
      : order.confirmation ? "결제 인증 완료 · 승인 결과 확인 중" : "주문 생성 완료 · 결제 대기";
  const result = order.latestPayment || order.receipt;
  element("result-details").hidden = !result;
  element("result").textContent = result ? JSON.stringify(result, null, 2) : "";
}

async function run(action) {
  busy = true;
  render();
  try {
    await action();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      sessionStorage.removeItem(AUTH_STORAGE);
      auth = null;
      config = null;
      message("로그인이 만료되었습니다. 같은 계정으로 다시 로그인하면 진행 중인 결제를 이어서 확인합니다.", "error");
    } else {
      message(error.message || "요청을 확인하지 못했습니다. 같은 주문에서 다시 확인해주세요.", "error");
    }
  } finally {
    busy = false;
    render();
  }
}

const api = (path, options = {}) => requestApi(path, { token: auth?.accessToken, ...options });

function loadSdk() {
  if (window.TossPayments) return Promise.resolve();
  if (!sdkLoading) {
    sdkLoading = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = "https://js.tosspayments.com/v2/standard";
      script.referrerPolicy = "no-referrer";
      script.onload = () => window.TossPayments ? resolve() : reject(new Error("토스 결제창을 불러오지 못했습니다."));
      script.onerror = () => { script.remove(); reject(new Error("토스 SDK에 연결하지 못했습니다. 네트워크를 확인해주세요.")); };
      document.head.append(script);
    }).catch((error) => { sdkLoading = null; throw error; });
  }
  return sdkLoading;
}

async function initializeCheckout() {
  if (order?.confirmation && !order.terminal) {
    await confirmCurrentOrder();
    return;
  }
  config = await api("/api/me/payments/checkout-config");
  await loadSdk();
}

async function confirmCurrentOrder() {
  if (!auth) {
    message("결제 인증이 완료되었습니다. 주문한 계정으로 로그인하면 승인을 이어서 진행합니다.");
    return;
  }
  message("결제 승인 결과를 확인하고 있습니다. 이 탭을 유지해주세요.");
  try {
    const receipt = await confirmOrder(order, auth.accessToken);
    saveOrder({ ...order, receipt, terminal: true });
    message("결제가 완료되었습니다. 주문 내역을 확인해주세요.", "success");
  } catch (error) {
    // 확정 실패 때만 서버 주문 상태를 확인한다. 통신 오류·결과 불명은 같은 승인 요청을 보존한다.
    if (["PAY004", "PAY005", "PAY008"].includes(error.code)) {
      const actual = await api(`/api/me/purchases/${order.purchaseId}`);
      if (actual.status !== "PENDING_PAYMENT") saveOrder({ ...order, status: actual.status, terminal: true });
    }
    if (error.code === "PAY009" || error.code === "PAY007") {
      throw new Error("아직 결제 결과를 확인 중입니다. 새 결제를 시작하지 말고 ‘결제 승인 다시 확인’을 눌러주세요.");
    }
    throw error;
  }
}

async function loadProducts() {
  products = [];
  element("products").replaceChildren();
  if (!element("cinema").value) return;
  products = await api(`/api/cinemas/${element("cinema").value}/products`);
  for (const product of products) {
    const row = document.createElement("div");
    row.className = "product";
    const label = document.createElement("label");
    label.htmlFor = `product-${product.productId}`;
    label.textContent = product.name;
    const description = document.createElement("small");
    description.textContent = `${won(product.price)} · 재고 ${product.quantity}개`;
    label.append(description);
    const input = document.createElement("input");
    Object.assign(input, { id: label.htmlFor, type: "number", min: "0", max: String(product.quantity), step: "1", value: "0" });
    input.dataset.productId = product.productId;
    input.setAttribute("aria-label", `${product.name} 수량`);
    row.append(label, input);
    element("products").append(row);
  }
  if (!products.length) element("products").textContent = "이 영화관에 등록된 상품이 없습니다.";
}

element("login-form").addEventListener("submit", (event) => {
  event.preventDefault();
  if (busy) return;
  run(async () => {
    const username = element("username").value.trim();
    if (order && order.username !== username) throw new Error("진행 중인 주문을 만든 계정으로 로그인해주세요.");
    const result = await requestApi("/login", { method: "POST", body: { username, password: element("password").value } });
    element("password").value = "";
    if (!result.accessToken) throw new Error("로그인 응답에 인증 정보가 없습니다.");
    const next = { username, accessToken: result.accessToken,
      customerKey: order?.customerKey || (auth?.username === username ? auth.customerKey : null) || crypto.randomUUID() };
    sessionStorage.setItem(AUTH_STORAGE, JSON.stringify(next));
    auth = next;
    config = null;
    message("로그인했습니다. 상품을 선택하거나 진행 중인 주문의 결제를 이어서 진행해주세요.", "success");
    await initializeCheckout();
  });
});

element("cinema").addEventListener("change", () => { if (!busy) run(loadProducts); });

element("order-form").addEventListener("submit", (event) => {
  event.preventDefault();
  if (busy || order) return;
  run(async () => {
    if (!auth || !config) throw new Error("로그인 후 결제창 설정을 확인해주세요.");
    const items = [...document.querySelectorAll("[data-product-id]")]
      .map((input) => ({ productId: Number(input.dataset.productId), quantity: Number(input.value) }))
      .filter((item) => item.quantity > 0);
    if (!items.length) throw new Error("상품을 한 개 이상 선택해주세요.");
    const created = await api("/api/me/purchases", {
      method: "POST", body: { cinemaId: Number(element("cinema").value), items },
    });
    const firstName = products.find((product) => product.productId === items[0].productId).name;
    saveOrder({ ...created, username: auth.username, customerKey: auth.customerKey,
      orderName: items.length > 1 ? `${firstName} 외 ${items.length - 1}종` : firstName,
      idempotencyKey: crypto.randomUUID(), confirmation: null, receipt: null, terminal: false });
    message("주문을 생성했습니다. 토스 결제창에서 결제를 진행해주세요.", "success");
  });
});

element("pay-button").addEventListener("click", () => {
  if (busy || !order || order.confirmation || order.terminal) return;
  run(async () => {
    if (!auth || !config) throw new Error("먼저 로그인해주세요.");
    const actual = await api(`/api/me/purchases/${order.purchaseId}`);
    if (actual.status !== "PENDING_PAYMENT") {
      saveOrder({ ...order, status: actual.status, terminal: true });
      throw new Error("결제 대기 상태의 주문이 아닙니다. 주문 내역을 확인해주세요.");
    }
    saveOrder({ ...order, totalAmount: actual.totalAmount });
    const payment = window.TossPayments(config.clientKey).payment({ customerKey: order.customerKey });
    message("토스 결제창으로 이동합니다.");
    await payment.requestPayment(buildPaymentRequest(order, window.location.origin));
  });
});

element("confirm-button").addEventListener("click", () => { if (!busy) run(confirmCurrentOrder); });
element("lookup-button").addEventListener("click", () => {
  if (!busy) run(async () => {
    const latestPayment = await api(`/api/me/payments/${order.receipt.paymentId}`);
    saveOrder({ ...order, latestPayment });
    message(`최신 결제 상태: ${latestPayment.status} · 남은 금액 ${won(latestPayment.remainingAmount)}`, "success");
  });
});
element("new-order-button").addEventListener("click", () => {
  if (!busy && order?.terminal) run(async () => {
    saveOrder(null);
    element("result-details").open = false;
    if (auth) await initializeCheckout();
    await loadProducts();
    message("새 주문의 상품을 선택해주세요.");
  });
});

run(async () => {
  // SDK 등 외부 스크립트를 불러오기 전에 인증 결과를 저장하고 주소창에서 제거한다.
  const returned = new URLSearchParams(window.location.search);
  const search = window.location.search;
  if (returned.has("flow")) window.history.replaceState(null, "", window.location.pathname);
  sessionStorage.setItem("cgv-payment-storage-check", "ok");
  sessionStorage.removeItem("cgv-payment-storage-check");
  auth = JSON.parse(sessionStorage.getItem(AUTH_STORAGE) || "null");
  order = JSON.parse(sessionStorage.getItem(ORDER_STORAGE) || "null");
  element("username").value = order?.username || auth?.username || "";
  // 메뉴 조회 실패가 이미 인증된 결제의 승인을 막지 않도록 독립적으로 처리한다.
  try {
    const cinemas = await api("/api/cinemas", { token: undefined });
    for (const cinema of cinemas) {
      const option = document.createElement("option");
      option.value = cinema.id;
      option.textContent = cinema.name;
      element("cinema").append(option);
    }
  } catch {
    message("영화관 목록을 불러오지 못했습니다. 새 주문을 만들려면 페이지를 다시 열어주세요.", "error");
  }
  if (returned.get("flow") === "success") saveOrder(captureAuthentication(order, search));
  if (returned.get("flow") === "fail") {
    message(`결제 인증이 완료되지 않았습니다. ${returned.get("message") || "같은 주문으로 다시 시도할 수 있습니다."}`, "error");
  }
  if (order?.confirmation && !order.terminal && !auth) await confirmCurrentOrder();
  if (auth) await initializeCheckout();
});
