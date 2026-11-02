package com.example.demo.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceCommandMatcherTest {

    @Test
    fun testPositiveReadCards_samePhoneticAndTone() {
        val readSamples = listOf(
            "读牌", "独牌", "毒牌", "读排", "独排", "毒排",
            "报牌", "暴牌", "抱牌", "豹牌", "爆牌", "报排",
            "读我的牌", "报我的牌", "睇牌", "睇我嘅牌", "睇我啲牌",
            "讀牌", "讀排", "報牌", "念牌", "念一下",
            "出什么", "怎么出", "睇下", "睇吓", "睇下牌", "睇吓牌",
            "點樣出", "點樣出牌", "有咩牌", "有乜牌", "出咩牌",
            "read cards", "read hand", "what to play",
            "read", "report", "speak", "cards", "play"
        )

        for (sample in readSamples) {
            val result = VoiceCommandMatcher.matchCommand(sample)
            assertEquals(
                GameVoiceCommand.READ_CARDS,
                result,
                "Expected READ_CARDS for input: '$sample'"
            )
        }
    }

    @Test
    fun testPositiveLockCards_samePhoneticAndTone() {
        val lockSamples = listOf(
            "锁定", "所定", "索定", "锁牌", "所牌", "锁住", "所住",
            "鎖定", "瑣定", "锁卡", "所卡", "锁手牌", "得喇", "得啦", "定牌",
            "lock card", "lock cards", "lock the hand"
        )

        for (sample in lockSamples) {
            val result = VoiceCommandMatcher.matchCommand(sample)
            assertEquals(
                GameVoiceCommand.LOCK_CARDS,
                result,
                "Expected LOCK_CARDS for input: '$sample'"
            )
        }
    }

    @Test
    fun testPositiveNewCards_samePhoneticAndTone() {
        val newSamples = listOf(
            "新一局", "心一局", "辛一局", "新牌", "心牌", "重新发牌", "解锁",
            "下一局", "夏一局", "重新扫描", "重扫", "姐锁", "洗牌", "新一盤", "重開", "再嚟過",
            "new round", "new game", "next round", "reset cards", "unlock",
            "new", "next", "clear", "deal"
        )

        for (sample in newSamples) {
            val result = VoiceCommandMatcher.matchCommand(sample)
            assertEquals(
                GameVoiceCommand.NEW_CARDS,
                result,
                "Expected NEW_CARDS for input: '$sample'"
            )
        }
    }

    @Test
    fun testNegativeCases_wrongToneOrConsonantStrictlyRejected() {
        val invalidSamples = listOf(
            "杜牌", // dù (tone 4) instead of dú (2)
            "度牌", // dù (tone 4)
            "堵牌", // dǔ (tone 3)
            "都牌", // dōu (tone 1)
            "读派", // pài (tone 4) instead of pái (2)
            "杜派", // dù pài
            "读拍", // pāi (tone 1)
            "肚皮", // dù pí
            "督派", // dū pài
            "得排", // dé / děi / de
            "梭牌", // suō (tone 1) instead of suǒ (3)
            "梭定", // suō (tone 1)
            "新一曲", // qǔ (tone 3) instead of jú (2)
            "新一举", // jǔ (tone 3)
            "吃饭了",
            "你好小度",
            "不要锁"
        )

        for (sample in invalidSamples) {
            val result = VoiceCommandMatcher.matchCommand(sample)
            assertNull(
                result,
                "Expected NULL (rejection) for invalid tone/phonetic input: '$sample', got: $result"
            )
        }
    }

    @Test
    fun testLanguageScopedMatching_EnglishSetting() {
        // Under English mode, English commands succeed, Chinese commands are rejected
        assertEquals(GameVoiceCommand.READ_CARDS, VoiceCommandMatcher.matchCommand("read cards", "en-US"))
        assertEquals(GameVoiceCommand.LOCK_CARDS, VoiceCommandMatcher.matchCommand("lock cards", "en"))
        assertEquals(GameVoiceCommand.NEW_CARDS, VoiceCommandMatcher.matchCommand("new round", "en-US"))

        assertNull(VoiceCommandMatcher.matchCommand("报牌", "en-US"))
        assertNull(VoiceCommandMatcher.matchCommand("读牌", "en"))
        assertNull(VoiceCommandMatcher.matchCommand("锁定", "en-US"))
        assertNull(VoiceCommandMatcher.matchCommand("睇牌", "en"))
    }

    @Test
    fun testLanguageScopedMatching_ChineseSetting() {
        // Under Chinese mode, Mandarin commands succeed, English keywords are rejected
        assertEquals(GameVoiceCommand.READ_CARDS, VoiceCommandMatcher.matchCommand("报牌", "zh-CN"))
        assertEquals(GameVoiceCommand.READ_CARDS, VoiceCommandMatcher.matchCommand("读牌", "zh"))
        assertEquals(GameVoiceCommand.LOCK_CARDS, VoiceCommandMatcher.matchCommand("锁定", "zh-CN"))
        assertEquals(GameVoiceCommand.NEW_CARDS, VoiceCommandMatcher.matchCommand("新一局", "zh"))

        assertNull(VoiceCommandMatcher.matchCommand("read cards", "zh-CN"))
        assertNull(VoiceCommandMatcher.matchCommand("cards", "zh"))
        assertNull(VoiceCommandMatcher.matchCommand("lock cards", "zh-CN"))
        assertNull(VoiceCommandMatcher.matchCommand("clear", "zh"))
    }

    @Test
    fun testLanguageScopedMatching_CantoneseSetting() {
        // Under Cantonese mode, Cantonese & Chinese commands succeed, English keywords are rejected
        assertEquals(GameVoiceCommand.READ_CARDS, VoiceCommandMatcher.matchCommand("睇牌", "zh-HK"))
        assertEquals(GameVoiceCommand.READ_CARDS, VoiceCommandMatcher.matchCommand("睇下牌", "yue-Hant-HK"))
        assertEquals(GameVoiceCommand.READ_CARDS, VoiceCommandMatcher.matchCommand("睇我啲牌", "yue-Hant-HK"))
        assertEquals(GameVoiceCommand.READ_CARDS, VoiceCommandMatcher.matchCommand("有咩牌", "zh-HK"))
        assertEquals(GameVoiceCommand.READ_CARDS, VoiceCommandMatcher.matchCommand("出咩牌", "yue-Hant-HK"))
        assertEquals(GameVoiceCommand.READ_CARDS, VoiceCommandMatcher.matchCommand("點出", "yue-Hant-HK"))
        assertEquals(GameVoiceCommand.READ_CARDS, VoiceCommandMatcher.matchCommand("報牌", "zh-HK"))
        assertEquals(GameVoiceCommand.READ_CARDS, VoiceCommandMatcher.matchCommand("讀牌", "zh-HK"))
        assertEquals(GameVoiceCommand.READ_CARDS, VoiceCommandMatcher.matchCommand("點樣出牌", "zh-HK"))
        assertEquals(GameVoiceCommand.LOCK_CARDS, VoiceCommandMatcher.matchCommand("鎖定", "zh-HK"))
        assertEquals(GameVoiceCommand.LOCK_CARDS, VoiceCommandMatcher.matchCommand("搞掂", "yue-Hant-HK"))
        assertEquals(GameVoiceCommand.LOCK_CARDS, VoiceCommandMatcher.matchCommand("搞定", "zh-HK"))
        assertEquals(GameVoiceCommand.LOCK_CARDS, VoiceCommandMatcher.matchCommand("得喇", "zh-HK"))
        assertEquals(GameVoiceCommand.NEW_CARDS, VoiceCommandMatcher.matchCommand("新一局", "zh-HK"))
        assertEquals(GameVoiceCommand.NEW_CARDS, VoiceCommandMatcher.matchCommand("新一盤", "zh-HK"))
        assertEquals(GameVoiceCommand.NEW_CARDS, VoiceCommandMatcher.matchCommand("洗牌", "yue-Hant-HK"))
        assertEquals(GameVoiceCommand.NEW_CARDS, VoiceCommandMatcher.matchCommand("重開", "zh-HK"))

        assertNull(VoiceCommandMatcher.matchCommand("read cards", "zh-HK"))
        assertNull(VoiceCommandMatcher.matchCommand("cards", "yue-Hant-HK"))
        assertNull(VoiceCommandMatcher.matchCommand("lock cards", "zh-HK"))
        assertNull(VoiceCommandMatcher.matchCommand("play", "yue-Hant-HK"))
    }

    @Test
    fun testPositiveSwitchMode_samePhoneticAndTone() {
        val modeToggleSamples = listOf(
            "切换模式", "换模式", "转换模式", "切换玩法", "換模式", "轉模式", "轉下模式", "切換模式",
            "switch mode", "toggle mode", "change mode"
        )
        for (sample in modeToggleSamples) {
            val result = VoiceCommandMatcher.matchCommand(sample)
            assertEquals(GameVoiceCommand.SWITCH_MODE, result, "Expected SWITCH_MODE for: '$sample'")
        }

        val cddSamples = listOf(
            "锄大地", "切换到锄大地", "玩锄大地", "换成锄大地", "大老二", "大2", "鋤大D", "轉鋤大D", "玩鋤大D", "初大D",
            "switch to big two", "big two", "cho dai dee", "play big two", "cdd"
        )
        for (sample in cddSamples) {
            val result = VoiceCommandMatcher.matchCommand(sample)
            assertEquals(GameVoiceCommand.SWITCH_TO_CDD, result, "Expected SWITCH_TO_CDD for: '$sample'")
        }

        val ddzSamples = listOf(
            "斗地主", "切换到斗地主", "玩斗地主", "换成斗地主", "逗地主", "鬥地主", "轉鬥地主", "玩鬥地主",
            "switch to ddz", "dou di zhu", "play dou di zhu", "fight the landlord", "ddz"
        )
        for (sample in ddzSamples) {
            val result = VoiceCommandMatcher.matchCommand(sample)
            assertEquals(GameVoiceCommand.SWITCH_TO_DDZ, result, "Expected SWITCH_TO_DDZ for: '$sample'")
        }
    }

    @Test
    fun testPositiveSwitchRole_samePhoneticAndTone() {
        val roleToggleSamples = listOf(
            "切换身份", "换身份", "切换角色", "换角色", "切換身份", "換身份", "轉身份", "轉角色",
            "switch role", "toggle role", "change role"
        )
        for (sample in roleToggleSamples) {
            val result = VoiceCommandMatcher.matchCommand(sample)
            assertEquals(GameVoiceCommand.SWITCH_ROLE, result, "Expected SWITCH_ROLE for: '$sample'")
        }

        val landlordSamples = listOf(
            "地主", "切换到地主", "换成地主", "我是地主", "叫地主", "抢地主", "做地主", "做地主", "我係地主", "搶地主",
            "switch to landlord", "iam landlord", "landlord", "play landlord"
        )
        for (sample in landlordSamples) {
            val result = VoiceCommandMatcher.matchCommand(sample)
            assertEquals(GameVoiceCommand.SWITCH_TO_LANDLORD, result, "Expected SWITCH_TO_LANDLORD for: '$sample'")
        }

        val peasantSamples = listOf(
            "农民", "切换到农民", "换成农民", "我是农民", "做农民", "平民", "不叫", "不抢", "農民", "做農民", "我係農民",
            "switch to peasant", "iam peasant", "peasant", "farmer"
        )
        for (sample in peasantSamples) {
            val result = VoiceCommandMatcher.matchCommand(sample)
            assertEquals(GameVoiceCommand.SWITCH_TO_PEASANT, result, "Expected SWITCH_TO_PEASANT for: '$sample'")
        }
    }

    @Test
    fun testPositiveDdzVsLandlordDisambiguation() {
        // "斗地主" contains "地主", but MUST be recognized as SWITCH_TO_DDZ, NOT SWITCH_TO_LANDLORD
        assertEquals(GameVoiceCommand.SWITCH_TO_DDZ, VoiceCommandMatcher.matchCommand("斗地主"))
        assertEquals(GameVoiceCommand.SWITCH_TO_DDZ, VoiceCommandMatcher.matchCommand("鬥地主"))
        assertEquals(GameVoiceCommand.SWITCH_TO_DDZ, VoiceCommandMatcher.matchCommand("切换到斗地主"))

        // Pure "地主" or "抢地主" MUST be recognized as SWITCH_TO_LANDLORD
        assertEquals(GameVoiceCommand.SWITCH_TO_LANDLORD, VoiceCommandMatcher.matchCommand("地主"))
        assertEquals(GameVoiceCommand.SWITCH_TO_LANDLORD, VoiceCommandMatcher.matchCommand("抢地主"))
        assertEquals(GameVoiceCommand.SWITCH_TO_LANDLORD, VoiceCommandMatcher.matchCommand("我是地主"))
    }
}
