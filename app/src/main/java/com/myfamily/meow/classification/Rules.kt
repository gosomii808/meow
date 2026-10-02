package com.myfamily.meow.classification

/**
 * Brand part of a merchant name so branches share history:
 * "스타벅스 강남점" → "스타벅스", "GS25 역삼점" → "gs25".
 */
fun merchantKey(merchant: String): String =
    merchant.trim().split(Regex("""\s+""")).first().lowercase()

/** Step ③ of the classification chain: well-known merchants and keywords (spec §10). */
object RuleClassifier {
    private val RULES: List<Pair<Category, List<String>>> = listOf(
        Category.CAFE to listOf(
            "스타벅스", "커피", "카페", "이디야", "투썸", "메가mgc", "메가커피", "빽다방", "컴포즈", "할리스",
            "폴바셋", "블루보틀", "공차", "베스킨", "배스킨", "던킨", "파리바게뜨", "뚜레쥬르", "starbucks",
        ),
        Category.SUBSCRIPTION to listOf(
            "넷플릭스", "netflix", "유튜브", "youtube", "스포티파이", "spotify", "멜론", "티빙", "웨이브",
            "왓챠", "디즈니", "쿠팡와우", "chatgpt", "openai", "애플", "apple.com", "google play",
        ),
        Category.FOOD to listOf(
            "배달의민족", "배민", "요기요", "쿠팡이츠", "맥도날드", "버거킹", "롯데리아", "kfc", "맘스터치",
            "서브웨이", "김밥", "식당", "치킨", "피자", "분식", "국밥", "족발", "닭발", "떡볶이", "도시락",
            "학식", "구내식당", "푸드", "반점", "쌀국수", "초밥", "고기",
        ),
        Category.TRANSPORT to listOf(
            "지하철", "버스", "택시", "카카오t", "카카오모빌리티", "티머니", "캐시비", "코레일", "srt",
            "주유", "oil", "충전소", "따릉이", "킥보드", "쏘카", "그린카",
        ),
        Category.SHOPPING to listOf(
            "무신사", "쿠팡", "29cm", "지그재그", "에이블리", "올리브영", "11번가", "g마켓", "옥션",
            "네이버쇼핑", "ssg", "현대백화점", "신세계", "롯데백화점", "유니클로", "자라", "abc마트",
        ),
        Category.LIVING to listOf(
            "gs25", "cu", "세븐일레븐", "이마트24", "미니스톱", "편의점", "다이소", "이마트", "홈플러스",
            "롯데마트", "코스트코", "마트", "세탁", "통신", "kt", "skt", "lg u+",
        ),
        Category.CULTURE to listOf(
            "cgv", "메가박스", "롯데시네마", "노래방", "코인노래", "pc방", "피시방", "볼링", "인터파크티켓",
            "yes24티켓", "멜론티켓", "방탈출",
        ),
        Category.EDUCATION to listOf(
            "교보문고", "알라딘", "영풍문고", "yes24", "문구", "학원", "인프런", "클래스101", "토익", "서점",
        ),
        Category.MEDICAL to listOf("약국", "병원", "의원", "치과", "한의원", "안과", "피부과"),
        Category.TRAVEL to listOf(
            "호텔", "에어비앤비", "airbnb", "야놀자", "여기어때", "항공", "대한항공", "아시아나", "제주항공",
            "진에어", "티웨이", "아고다", "트립닷컴", "숙박",
        ),
    )

    fun classify(merchant: String): Category? {
        val m = merchant.lowercase()
        val key = merchantKey(merchant)
        // Whole-token match for short brand names like "cu"/"kt" so "cucumber" doesn't hit.
        return RULES.firstOrNull { (_, words) ->
            words.any { w -> if (w.length <= 3) key == w else w in m }
        }?.first
    }
}

/** Charges, transfers and withdrawals are usually not purchases (spec §9). */
object TransferDetector {
    private val KEYWORDS = Regex("충전|송금|이체|계좌|출금|정산|보냈")

    fun isTransferLike(vararg texts: String): Boolean = texts.any { KEYWORDS.containsMatchIn(it) }
}
