const checkout = document.getElementById("checkout");
const inputs = document.getElementById("inputs");
const prepareButton = document.getElementById("prepareButton");
const payButton = document.getElementById("payButton");
const orderSummary = document.getElementById("orderSummary");
const result = document.getElementById("result");

let preparedPayment = null;
let widgets = null;

function show(value) {
    result.textContent = typeof value === "string"
        ? value
        : JSON.stringify(value, null, 2);
}

// 1. 예매에 대한 결제를 준비하고 주문서 UI를 표시한다.
prepareButton.addEventListener("click", async () => {
    prepareButton.disabled = true;
    payButton.disabled = true;

    try {
        const clientKey =
            document.getElementById("clientKey").value.trim();

        const token =
            document.getElementById("token").value.trim();

        const targetId = Number(document.getElementById("targetId").value);
        const isStoreOrder = document.getElementById("targetType").value === "STORE_ORDER";

        if (!clientKey.startsWith("test_gck")) {
            throw new Error(
                "주문서형 테스트 클라이언트 키를 입력하세요."
            );
        }

        if (!token) {
            throw new Error("로그인 accessToken을 입력하세요.");
        }

        if (!Number.isSafeInteger(targetId) || targetId <= 0) {
            throw new Error("유효한 예매 또는 주문 ID를 입력하세요.");
        }

        // 우리 서버에서 결제 대기 정보를 저장하거나 재사용한다.
        const response = await fetch(
            isStoreOrder ? "/api/payments/store-orders/prepare" : "/api/payments/reservations/prepare",
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}`
                },
                body: JSON.stringify(isStoreOrder ? { storeOrderId: targetId } : { reservationId: targetId })
            }
        );

        const body = await response.json();

        if (!response.ok) {
            throw new Error(
                body.message ?? "결제 준비에 실패했습니다."
            );
        }

        // 로컬 테스트용 고객 식별자
        let customerKey = sessionStorage.getItem("testCustomerKey");

        if (!customerKey) {
            customerKey = crypto.randomUUID();
            sessionStorage.setItem("testCustomerKey", customerKey);
        }

        const tossPayments = TossPayments(clientKey);
        widgets = tossPayments.widgets({ customerKey });

        // 금액을 먼저 설정해야 결제수단 UI를 렌더링할 수 있다.
        await widgets.setAmount({
            currency: "KRW",
            value: body.amount
        });

        await widgets.renderPaymentMethods({
            selector: "#payment-method",
            variantKey: "DEFAULT"
        });

        await widgets.renderAgreement({
            selector: "#agreement",
            variantKey: "AGREEMENT"
        });

        preparedPayment = body;

        orderSummary.textContent =
            `${body.orderName} / ${body.amount.toLocaleString()}원`;

        inputs.hidden = true;
        payButton.disabled = false;

        show(body);
    } catch (error) {
        show(
            `${error.message}\n입력값과 설정을 확인한 뒤 새로고침하세요.`
        );
    }
});

// 2. 사용자가 선택한 결제수단으로 인증을 요청한다.
payButton.addEventListener("click", async () => {
    if (!widgets || !preparedPayment) {
        return;
    }

    payButton.disabled = true;

    try {
        await widgets.requestPayment({
            orderId: preparedPayment.orderId,
            orderName: preparedPayment.orderName,
            successUrl:
                `${location.origin}/payment-test.html?result=success`,
            failUrl:
                `${location.origin}/payment-test.html?result=fail`
        });
    } catch (error) {
        show(error.message);
    } finally {
        payButton.disabled = false;
    }
});

// 3. 인증 결과를 확인하고 우리 서버에 승인을 요청한다.
const params = new URLSearchParams(location.search);
const paymentResult = params.get("result");

const confirmSection = document.getElementById("confirmSection");
const confirmButton = document.getElementById("confirmButton");

if (paymentResult) {
    checkout.hidden = true;

    if (paymentResult === "success") {
        confirmSection.hidden = false;

        show({
            message: "결제 인증 성공. 서버 승인을 진행하세요.",
            orderId: params.get("orderId"),
            amount: params.get("amount")
        });
    } else {
        show({
            message: "결제가 중단되었거나 인증에 실패했습니다.",
            code: params.get("code"),
            detail: params.get("message")
        });
    }
}

confirmButton.addEventListener("click", async () => {
    confirmButton.disabled = true;

    try {
        const token = document
            .getElementById("confirmToken")
            .value.trim();

        const paymentKey = params.get("paymentKey");
        const orderId = params.get("orderId");
        const amount = Number(params.get("amount"));

        if (!token) {
            throw new Error("로그인 accessToken을 입력하세요.");
        }

        if (!paymentKey || !orderId
            || !Number.isSafeInteger(amount)
            || amount <= 0) {
            throw new Error("인증 결과의 결제 정보가 올바르지 않습니다.");
        }

        const response = await fetch("/api/payments/confirm", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${token}`
            },
            body: JSON.stringify({
                paymentKey,
                orderId,
                amount
            })
        });

        const body = await response.json();

        if (!response.ok) {
            show(body);
            return;
        }

        const purchaseCompleted = body.reservationStatus === "Reserved" || body.storeOrderStatus === "PAID";
        if (body.status !== "DONE" || !purchaseCompleted) {
            throw new Error("결제 완료 상태를 확인할 수 없습니다.");
        }

        show({
            message: body.storeOrderId ? "결제와 매점 주문이 완료되었습니다." : "결제와 영화 예매가 완료되었습니다.",
            ...body
        });

        document.getElementById("confirmToken").value = "";
        confirmSection.hidden = true;

    } catch (error) {
        show({
            message: error.message,
            안내: "통신 오류라면 새 결제를 만들지 말고 같은 화면에서 결과를 다시 확인하세요."
        });
    } finally {
        confirmButton.disabled = false;
    }
});
