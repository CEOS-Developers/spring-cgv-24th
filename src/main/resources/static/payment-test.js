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

        const reservationId =
            Number(document.getElementById("reservationId").value);

        if (!clientKey.startsWith("test_gck")) {
            throw new Error(
                "주문서형 테스트 클라이언트 키를 입력하세요."
            );
        }

        if (!token) {
            throw new Error("로그인 accessToken을 입력하세요.");
        }

        if (!Number.isSafeInteger(reservationId) || reservationId <= 0) {
            throw new Error("유효한 예매 ID를 입력하세요.");
        }

        // 우리 서버에서 결제 대기 정보를 저장하거나 재사용한다.
        const response = await fetch(
            "/api/payments/reservations/prepare",
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}`
                },
                body: JSON.stringify({ reservationId })
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

// 3. 토스 인증 이후 돌아온 결과를 표시한다.
const params = new URLSearchParams(location.search);
const paymentResult = params.get("result");

if (paymentResult) {
    checkout.hidden = true;

    if (paymentResult === "success") {
        show({
            message: "결제 인증 성공. 아직 서버 승인 전입니다.",
            paymentKey: params.get("paymentKey"),
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