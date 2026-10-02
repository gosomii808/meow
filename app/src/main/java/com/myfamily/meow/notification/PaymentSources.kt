package com.myfamily.meow.notification

/** Apps whose notifications are inspected. Everything else is ignored without being stored. */
object PaymentSources {
    private val FINANCE_APPS = mapOf(
        "viva.republica.toss" to "토스",
        "com.kakaopay.app" to "카카오페이",
        "com.kakaobank.channel" to "카카오뱅크",
        "com.kbankwith.smartbank" to "케이뱅크",
        "com.nonghyup.nhallonebank" to "NH올원뱅크",
        "kr.co.cu.onbank" to "신협",
        "com.samsung.android.spay" to "삼성 월렛",
        "com.bizplay.seoul.pay" to "서울페이",
        "com.komsco.kpay" to "지역화폐",
        "com.shinhan.o2o" to "신한",
        "com.shcard.smartpay" to "신한카드",
        "com.kbcard.cxh.appcard" to "KB국민카드",
        "kr.co.samsungcard.mpocket" to "삼성카드",
        "com.hyundaicard.appcard" to "현대카드",
        "com.naverfin.payapp" to "네이버페이",
    )

    /** Chat/SMS apps: only messages whose sender looks like a financial institution. */
    private val MESSAGING_APPS = mapOf(
        "com.kakao.talk" to "카카오톡",
        "com.samsung.android.messaging" to "문자",
        "com.google.android.apps.messaging" to "문자",
    )

    private val FINANCIAL_SENDER = Regex("카드|은행|뱅크|페이|Pay|금고|신협|농협|우체국|증권")

    private const val KAKAO_TALK = "com.kakao.talk"

    /** KakaoPay settlement/transfer received inside KakaoTalk: "12,000원을 받았어요". */
    private val KAKAO_SETTLEMENT = Regex("""\d[\d,]*\s*원을?\s*받았어요""")

    /** The settlement-receipt wording, independent of the app (used for debug fakes too). */
    fun isSettlementText(title: String, text: String): Boolean =
        KAKAO_SETTLEMENT.containsMatchIn("$title $text")

    /**
     * The one KakaoTalk message we want despite the sender filter: a KakaoPay settlement receipt.
     * Sending money gives no notification, but receiving shows "N원을 받았어요" (spec: 정산 수신).
     */
    fun isKakaoSettlement(packageName: String, title: String, text: String): Boolean =
        packageName == KAKAO_TALK && isSettlementText(title, text)

    fun isSource(packageName: String) = packageName in FINANCE_APPS || packageName in MESSAGING_APPS

    /** For messaging apps the sender (title) must look financial; SMS "[Web발신]" also counts. */
    fun passesSenderFilter(packageName: String, title: String, text: String): Boolean =
        packageName !in MESSAGING_APPS || FINANCIAL_SENDER.containsMatchIn(title) || "Web발신" in text

    /** Card/bank name for display: messaging sender name if financial, else app name. */
    fun label(packageName: String, title: String): String {
        FINANCE_APPS[packageName]?.let { return it }
        return FINANCIAL_SENDER.find(title)?.let { title.trim().take(12) }
            ?: MESSAGING_APPS[packageName]
            ?: packageName
    }
}
