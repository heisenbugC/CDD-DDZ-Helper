package com.example.demo.services

/**
 * Pure same-phonetic, same-tone (同音同调) Voice Trigger Matcher.
 * Accurately handles homophones, dialect variations (Mandarin, Cantonese),
 * and language-scoped matching to strictly honor user voice settings.
 */
object VoiceCommandMatcher {

    // 1. LOCK_CARDS:
    // Mandarin: suǒ (tone 3) + [dìng (4) | zhù (4) | pái (2) | kǎ]
    // Cantonese: so2 ding6 (鎖定), so2 paai4 (鎖牌), ding6 paai4 (定牌), dak1 laa3 (得喇/得啦), ding6 zo2 (定咗), ting4 (停), gaau2 dim1 (搞掂/搞定)
    private val LOCK_CHINESE_REGEX = Regex(
        "[锁鎖所索琐瑣]([定订訂锭錠]|[住注助驻駐祝铸鑄]|手?([牌排徘]|卡))|[锁鎖]牌|定牌|得[喇啦]|好[喇啦]|定[咗左]|已[锁鎖]定|搞[掂定][喇啦]?|搞好|收牌|停牌?|定晒"
    )

    private val LOCK_ENGLISH_KEYWORDS = listOf(
        "lockcard", "lockcards", "lockthecard", "lockthecards",
        "lockhand", "lockthehand", "lockmycards", "lockmycard", "lock"
    )

    // 2. NEW_CARDS:
    // Mandarin: xīn (1) / xià (4) + (yī)? + jú (2) | xīn (1) + pái (2) | jiě (3) + suǒ (3) | chóng xīn fā pái | xǐ pái
    // Cantonese: san1 jat1 guk6 (新一局), san1 jat1 pun4 (新一盤/新一盘), san1 jat1 leon4 (新一輪/轮), sai2 paai4 (洗牌), gaai2 so2 (解鎖/解锁), cung4 hoi1 (重開/重开), zoi3 lei4 (再嚟/再來), cung4 san1 faat3 paai4 (重新發牌)
    private val NEW_ROUND_CHINESE_REGEX = Regex(
        "([新心辛欣薪馨锌身]|[下夏])一?([局橘菊焗桔]|[盘盤]|[轮輪])|洗牌|细牌|細牌|西牌"
    )

    private val NEW_CARDS_CHINESE_REGEX = Regex(
        "[新心辛欣薪馨锌身](手)?([牌排徘]|卡)"
    )

    private val NEW_DEAL_CHINESE_REGEX = Regex(
        "(重新|重)?([新心辛欣薪])[发發]([牌排徘]|卡)|重[开開]|重新[开開]始|再[嚟来來]過?|再玩|重嚟"
    )

    private val UNLOCK_CHINESE_REGEX = Regex(
        "[解姐改][锁鎖所索琐瑣]"
    )

    private val RESCAN_CHINESE_REGEX = Regex(
        "(重新|重)(扫描|扫|描)"
    )

    private val NEW_ENGLISH_KEYWORDS = listOf(
        "newcard", "newcards", "newround", "newgame", "nextround", "nextgame",
        "unlockcard", "unlockcards", "unlock", "resetcard", "resetcards", "reset",
        "new", "next", "clear", "deal"
    )

    // 3. READ_CARDS:
    // Mandarin: dú (2) / bào (4) / kàn (4) / niàn (4) + [pái (2) / kǎ]
    // Cantonese:
    // tai2 (2): 睇, 体, 體, 弟, 第, 底, 剃, 梯, 替, 提, 啼, 题, 題
    // duk6 (6): 读, 讀, 独, 獨, 毒, 督, 篤, 犊, 牍, 嘟
    // bou3 (3): 报, 報, 保, 宝, 寶, 步, 抱, 暴, 爆, 豹, 鲍, 鮑, 布, 怖
    // nim6 (6): 念, 唸, 廿
    // gong2 (2): 讲, 講
    // caa4 (4): 查, 茶
    // Spoken queries: 睇牌, 睇下牌, 睇下啲牌, 睇下, 睇睇, 睇睇牌, 睇下有咩牌, 有咩牌, 有乜牌, 有咩出, 點出, 點出牌, 點樣出, 點樣出牌, 報牌, 報下牌, 讀牌, 讀下牌, 講牌, 講下牌, 念牌, 唸牌, 查牌, 鋤大D, 出咩牌, 出乜牌
    private val READ_CARDS_CHINESE_REGEX = Regex(
        "([读讀独獨毒督篤犊渎椟黩嘟]|[报報保宝寶步抱暴豹爆鲍鮑布怖]|[看瞰阚]|[睇体體弟第底梯替提啼题題]|[念唸廿]|[出初]|[讲講]|[查茶])(我的|我嘅|我啲|一下|一吓|下|吓|睇)?([牌排徘]|卡)|我([啲的]|手|副)?牌|睇[下吓睇]?我([啲的]|手|副)?牌"
    )

    private val READ_QUERY_CHINESE_REGEX = Regex(
        "[出初](什么|甚么|么|麼|咩|乜)(牌)?|(怎么|怎樣|怎样|点|點|點樣)[出初打](牌)?|有(什么|甚么|咩|乜)(牌|出)?|读一下|报一下|看一下|念一下|睇下|睇吓|睇睇|睇下牌|睇吓牌|睇睇牌|讀下|報下|講下|講牌|講出[嚟来]|查下|有咩牌|有乜牌|點出|點樣出|點出牌|出咩|出乜"
    )

    private val READ_ENGLISH_KEYWORDS = listOf(
        "readcard", "readcards", "readthecard", "readthecards", "readmycard", "readmycards",
        "readhand", "readthehand", "readout", "readaloud", "whatcard", "whatcards", "whattoplay",
        "read", "report", "speak", "cards", "play"
    )

    // 4. SWITCH_MODE (Toggle CDD <-> DDZ):
    private val MODE_SWITCH_CHINESE_REGEX = Regex(
        "(切[换換]|换|更[改换換]|改[变變]|轉[换換]|转[换換])[个個]?(模式|规则|規則|游戏|遊戲|玩法)|[轉转換换切][換换]?[下吓]?(模式|玩法|遊戲)|切[换換]玩法"
    )

    private val MODE_SWITCH_ENGLISH_KEYWORDS = listOf(
        "switchmode", "togglemode", "changemode", "switchgame", "changegame"
    )

    // 5. SWITCH_TO_CDD (Cho Dai Dee / 锄大地):
    private val CDD_SWITCH_CHINESE_REGEX = Regex(
        "(切[换換到]|换[到成]?|玩|轉[到去]?|转换到?|切到)?([锄鋤除储初出][大][大地弟第底递遞啲Dd]|大[老二]|大2)|(切[换換到]|换[到成]?|玩)([锄鋤除储初出][大][大地弟第底递遞啲Dd])"
    )

    private val CDD_SWITCH_ENGLISH_KEYWORDS = listOf(
        "switchtocdd", "switchtobigtwo", "bigtwo", "chodaidee", "playbigtwo", "playcdd", "cdd"
    )

    // 6. SWITCH_TO_DDZ (Dou Di Zhu / 斗地主):
    private val DDZ_SWITCH_CHINESE_REGEX = Regex(
        "(切[换換到]|换[到成]?|玩|轉[到去]?|转换到?|切到)?([斗鬥逗豆抖][地帝递第][主祝注煮])"
    )

    private val DDZ_SWITCH_ENGLISH_KEYWORDS = listOf(
        "switchtoddz", "switchtodoudizhu", "doudizhu", "fightthelandlord", "playddz", "playdoudizhu", "ddz"
    )

    // 7. SWITCH_ROLE (Toggle Landlord <-> Peasant):
    private val ROLE_SWITCH_CHINESE_REGEX = Regex(
        "(切[换換]|换|更[改换換]|改[变變]|轉[换換]|转[换換])[个個]?(身份|身分|角色|脚色)|[轉转換换切][換换]?[下吓]?(身份|身分|角色)"
    )

    private val ROLE_SWITCH_ENGLISH_KEYWORDS = listOf(
        "switchrole", "togglerole", "changerole", "switchidentity"
    )

    // 8. SWITCH_TO_LANDLORD (Landlord / 地主):
    private val LANDLORD_CHINESE_REGEX = Regex(
        "(切[换換到]|换[到成]?|当|當|我是|我係|做|叫|抢|搶|选|選|轉做|揀)[地帝第][主祝注煮]|叫地主|抢地主|搶地主|(?<![斗鬥逗豆抖])[地帝第][主祝注煮]"
    )

    private val LANDLORD_ENGLISH_KEYWORDS = listOf(
        "switchtolandlord", "iamlandlord", "landlord", "playlandlord", "becomelandlord"
    )

    // 9. SWITCH_TO_PEASANT (Peasant / 农民):
    private val PEASANT_CHINESE_REGEX = Regex(
        "(切[换換到]|换[到成]?|当|當|我是|我係|做|选|選|轉做|揀)?([农農浓侬][民名明]|平民|农夫)|不叫|不[抢搶]"
    )

    private val PEASANT_ENGLISH_KEYWORDS = listOf(
        "switchtopeasant", "switchtofarmer", "iampeasant", "iamfarmer", "peasant", "farmer", "playpeasant"
    )

    /**
     * Matches raw speech recognition transcript against supported game voice commands.
     * When [languageCode] is supplied (e.g. "zh", "zh-CN", "zh-HK", "en", "en-US"):
     * - "en": Only matches English commands; rejects Chinese/Cantonese phrases.
     * - "zh" / "zh-HK": Only matches Chinese/Cantonese phrases; rejects English keywords.
     * - null: Matches both (backward-compatible).
     */
    fun matchCommand(rawText: String?, languageCode: String? = null): GameVoiceCommand? {
        if (rawText.isNullOrBlank()) return null

        val cleaned = rawText.lowercase().replace(" ", "").replace("_", "")

        val isEnglishOnly = languageCode != null && languageCode.startsWith("en", ignoreCase = true)
        val isChineseOnly = languageCode != null && (languageCode.startsWith("zh", ignoreCase = true) || languageCode.startsWith("yue", ignoreCase = true))

        if (isEnglishOnly) {
            // Strictly check English keywords only
            if (DDZ_SWITCH_ENGLISH_KEYWORDS.any { cleaned.contains(it) }) return GameVoiceCommand.SWITCH_TO_DDZ
            if (CDD_SWITCH_ENGLISH_KEYWORDS.any { cleaned.contains(it) }) return GameVoiceCommand.SWITCH_TO_CDD
            if (MODE_SWITCH_ENGLISH_KEYWORDS.any { cleaned.contains(it) }) return GameVoiceCommand.SWITCH_MODE

            if (LANDLORD_ENGLISH_KEYWORDS.any { cleaned.contains(it) }) return GameVoiceCommand.SWITCH_TO_LANDLORD
            if (PEASANT_ENGLISH_KEYWORDS.any { cleaned.contains(it) }) return GameVoiceCommand.SWITCH_TO_PEASANT
            if (ROLE_SWITCH_ENGLISH_KEYWORDS.any { cleaned.contains(it) }) return GameVoiceCommand.SWITCH_ROLE

            if (NEW_ENGLISH_KEYWORDS.any { cleaned.contains(it) }) return GameVoiceCommand.NEW_CARDS
            if (LOCK_ENGLISH_KEYWORDS.any { cleaned.contains(it) }) return GameVoiceCommand.LOCK_CARDS
            if (READ_ENGLISH_KEYWORDS.any { cleaned.contains(it) }) return GameVoiceCommand.READ_CARDS
            return null
        }

        if (isChineseOnly) {
            // Strictly check Chinese & Cantonese homophones and phrases; reject English keywords
            if (DDZ_SWITCH_CHINESE_REGEX.containsMatchIn(cleaned)) {
                return GameVoiceCommand.SWITCH_TO_DDZ
            }
            if (CDD_SWITCH_CHINESE_REGEX.containsMatchIn(cleaned)) {
                return GameVoiceCommand.SWITCH_TO_CDD
            }
            if (MODE_SWITCH_CHINESE_REGEX.containsMatchIn(cleaned)) {
                return GameVoiceCommand.SWITCH_MODE
            }

            if (LANDLORD_CHINESE_REGEX.containsMatchIn(cleaned)) {
                return GameVoiceCommand.SWITCH_TO_LANDLORD
            }
            if (PEASANT_CHINESE_REGEX.containsMatchIn(cleaned)) {
                return GameVoiceCommand.SWITCH_TO_PEASANT
            }
            if (ROLE_SWITCH_CHINESE_REGEX.containsMatchIn(cleaned)) {
                return GameVoiceCommand.SWITCH_ROLE
            }

            if (NEW_ROUND_CHINESE_REGEX.containsMatchIn(cleaned) ||
                NEW_CARDS_CHINESE_REGEX.containsMatchIn(cleaned) ||
                NEW_DEAL_CHINESE_REGEX.containsMatchIn(cleaned) ||
                UNLOCK_CHINESE_REGEX.containsMatchIn(cleaned) ||
                RESCAN_CHINESE_REGEX.containsMatchIn(cleaned)
            ) {
                return GameVoiceCommand.NEW_CARDS
            }

            if (LOCK_CHINESE_REGEX.containsMatchIn(cleaned)) {
                return GameVoiceCommand.LOCK_CARDS
            }

            if (READ_CARDS_CHINESE_REGEX.containsMatchIn(cleaned) ||
                READ_QUERY_CHINESE_REGEX.containsMatchIn(cleaned)
            ) {
                return GameVoiceCommand.READ_CARDS
            }

            return null
        }

        // Backward-compatible fallback (when languageCode is null): checks both
        if (DDZ_SWITCH_ENGLISH_KEYWORDS.any { cleaned.contains(it) } || DDZ_SWITCH_CHINESE_REGEX.containsMatchIn(cleaned)) {
            return GameVoiceCommand.SWITCH_TO_DDZ
        }
        if (CDD_SWITCH_ENGLISH_KEYWORDS.any { cleaned.contains(it) } || CDD_SWITCH_CHINESE_REGEX.containsMatchIn(cleaned)) {
            return GameVoiceCommand.SWITCH_TO_CDD
        }
        if (MODE_SWITCH_ENGLISH_KEYWORDS.any { cleaned.contains(it) } || MODE_SWITCH_CHINESE_REGEX.containsMatchIn(cleaned)) {
            return GameVoiceCommand.SWITCH_MODE
        }

        if (LANDLORD_ENGLISH_KEYWORDS.any { cleaned.contains(it) } || LANDLORD_CHINESE_REGEX.containsMatchIn(cleaned)) {
            return GameVoiceCommand.SWITCH_TO_LANDLORD
        }
        if (PEASANT_ENGLISH_KEYWORDS.any { cleaned.contains(it) } || PEASANT_CHINESE_REGEX.containsMatchIn(cleaned)) {
            return GameVoiceCommand.SWITCH_TO_PEASANT
        }
        if (ROLE_SWITCH_ENGLISH_KEYWORDS.any { cleaned.contains(it) } || ROLE_SWITCH_CHINESE_REGEX.containsMatchIn(cleaned)) {
            return GameVoiceCommand.SWITCH_ROLE
        }

        if (NEW_ENGLISH_KEYWORDS.any { cleaned.contains(it) } ||
            NEW_ROUND_CHINESE_REGEX.containsMatchIn(cleaned) ||
            NEW_CARDS_CHINESE_REGEX.containsMatchIn(cleaned) ||
            NEW_DEAL_CHINESE_REGEX.containsMatchIn(cleaned) ||
            UNLOCK_CHINESE_REGEX.containsMatchIn(cleaned) ||
            RESCAN_CHINESE_REGEX.containsMatchIn(cleaned)
        ) {
            return GameVoiceCommand.NEW_CARDS
        }

        if (LOCK_ENGLISH_KEYWORDS.any { cleaned.contains(it) } || LOCK_CHINESE_REGEX.containsMatchIn(cleaned)) {
            return GameVoiceCommand.LOCK_CARDS
        }

        if (READ_ENGLISH_KEYWORDS.any { cleaned.contains(it) } ||
            READ_CARDS_CHINESE_REGEX.containsMatchIn(cleaned) ||
            READ_QUERY_CHINESE_REGEX.containsMatchIn(cleaned)
        ) {
            return GameVoiceCommand.READ_CARDS
        }

        return null
    }
}
