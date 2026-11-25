package com.example.model

enum class ItemCategory(val displayName: String) {
    STAT_KEYWORD("Chỉ Số & Từ Khóa"),
    TRIGGER("Hiệu Ứng Kích Hoạt"),
    ECONOMY("Kinh Tế & Tiêu Hao")
}

data class ItemTemplate(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val category: ItemCategory,
    val description: String,
    val bonusPower: Int = 0,
    val bonusHealth: Int = 0,
    val costReduction: Int = 0,
    val grantedKeyword: Keyword? = null,
    val impactStacks: Int = 0,
    val spiritStacks: Int = 0,
    val onSummonStrikeWeakest: Boolean = false,
    val onSummonEphemeralCopy: Boolean = false,
    val onPlayDrawOne: Boolean = false,
    val onLastBreathRally: Boolean = false,
    val farsightGameStartDraw: Boolean = false,
    val refillManaOnPlay: Int = 0,
    val grantRandomKeywordOnSummon: Boolean = false,
    val forSpellsOnly: Boolean = false
)

data class RelicTemplate(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val description: String,
    val bonusPower: Int = 0,
    val bonusHealth: Int = 0,
    val grantedKeyword: Keyword? = null,
    val grantedKeywords: Set<Keyword> = emptySet(),
    val impactStacks: Int = 0,
    val spiritStacks: Int = 0,
    val alliesGrantedKeywords: Set<Keyword> = emptySet(),
    val alliesImpactStacks: Int = 0,
    val spellSkillDamageBonus: Int = 0,
    val elementalSpellSkillDamageBonus: Int = 0,
    val strikeWeakestOnSummon: Boolean = false,
    val refillSpellManaRoundStart: Boolean = false,
    val refillSpellManaOnPlay: Boolean = false,
    val attackGainPlusOnePlusOne: Boolean = false,
    val levelUpRally: Boolean = false,
    val startingManaBonus: Int = 0,
    val createChampionSpellRoundStart: Boolean = false,
    val nexusTough: Boolean = false,
    val adventureMaxHpBonus: Int = 0,
    val adventureRegenBonus: Int = 0,
    val adventureStartingGoldBonus: Int = 0,
    val adventureStartingRerollsBonus: Int = 0,
    val adventureStartingRevivesBonus: Int = 0,
    val goldMultiplierBonus: Double = 0.0
) {
    val allGrantedKeywords: Set<Keyword>
        get() = if (grantedKeyword != null) grantedKeywords + grantedKeyword else grantedKeywords
}

data class PowerTemplate(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val description: String,
    val spellSkillDamageBonus: Int = 0,
    val trigger: PowerTrigger? = null
)

enum class CardEffectType {
    NONE,
    DEAL_DAMAGE_ANY,
    DEAL_DAMAGE_NEXUS,
    DEAL_DAMAGE_UNIT_AND_NEXUS,
    STUN_TARGET,
    FROSTBITE_TARGET,
    GIVE_BARRIER_AND_DRAW,
    RALLY_AND_SHIELD,
    RECALL_OR_STUN,
    BUFF_ALLY_STATS,
    SUMMON_POROS,
    FORGE_STRONGEST,
    SINGLE_COMBAT_STRIKE,
    PLANT_CHIMES,
    PLANT_PUFFCAPS,
    PURGE_SHACKLES_CURSE,
    PURGE_SUPPRESSION_CURSE,
    PURGE_TERROR_CURSE,
    DISCARD_HAND_BURN,
    PREDICT_DRAW,
    MANIFEST_SPELL,
    FROSTBITE_TWO,
    ANIVIA_CHAMPION_SPELL,
    GLACIAL_STORM_SKILL,
    WINGS_OF_CRYOPHOENIX,
    SUMMON_TWO_GIANTS,
    DRAIN,
    FREE_ATTACK,
    RALLY,
    SACRIFICE_DAMAGE,
    POSSESS_UNIT,
    BREAKOUT_EFFECT,
    ASTRAL_DIFFUSION_EFFECT,
    STANCE_SWAP_EFFECT,
    BUFF_ALLIES_STATS
}

/** 1. Loại mục tiêu người chơi có thể chỉ định */
enum class TargetType {
    NONE,           // Không cần chọn mục tiêu (tự động / toàn bàn / Nhà Chính)
    UNIT,           // Chỉ chọn bài quân trên sân (loại trừ Địa Danh)
    NEXUS,          // Chỉ nhắm vào Nhà Chính
    UNIT_OR_NEXUS,  // Chọn bài quân hoặc Nhà Chính (vd: Phát Bắn Thần Bí)
    UNIT_PAIR       // Chọn 2 bài quân khác nhau (vd: Đấu Tay Đôi, Bàn Tay Chết Chóc)
}

/** 2. Phe hợp lệ của mục tiêu được chọn */
enum class TargetSide {
    NONE,           // Không chọn mục tiêu
    ALLY,           // Chỉ đồng minh
    ENEMY,          // Chỉ kẻ địch
    ANY,            // Cả đồng minh lẫn kẻ địch
    ALLY_AND_ENEMY  // Bắt buộc 1 đồng minh + 1 kẻ địch
}

/** 4. Phạm vi tác động thực tế của hiệu ứng khi phân giải */
enum class EffectScope {
    NONE,                    // Không có hiệu ứng phép/kỹ năng đặc biệt
    SINGLE_TARGET,           // Tác động lên 1 mục tiêu duy nhất
    MULTI_TARGET,            // Tác động lên nhiều mục tiêu đã chọn (vd: tối đa 2 kẻ địch)
    ENEMY_NEXUS,             // Tác động trực tiếp lên Nhà Chính địch
    ALL_ENEMIES,             // Toàn bộ bài quân địch trên sân
    ALL_ALLIES,              // Toàn bộ đồng minh trên sân
    ALL_UNITS,               // Toàn bộ bài quân trên cả 2 sân
    ALL_UNITS_EXCEPT_FILTER, // Toàn bộ bài quân trừ nhóm miễn nhiễm (vd: Phượng Hoàng Băng)
    RANDOM_UNIT              // Chọn ngẫu nhiên bài quân trên sân
}

/** Gói đặc tả mục tiêu & phạm vi gắn trực tiếp vào từng CardTemplate */
data class CardTargetingSpec(
    val targetType: TargetType = TargetType.NONE,
    val targetSide: TargetSide = TargetSide.NONE,
    val targetCount: Int = 0, // 3. Số lượng mục tiêu (0, 1, 2)
    val allowFewerTargets: Boolean = false, // Cho phép thi triển khi sân thiếu mục tiêu (vd: "tối đa 2 kẻ địch")
    val requireCanAttack: Boolean = false, // Ràng buộc riêng cho Tấn Công Tự Do
    val scope: EffectScope = EffectScope.NONE
) {
    val requiresManualTarget: Boolean
        get() = targetType != TargetType.NONE && targetType != TargetType.NEXUS && targetCount > 0
}

data class CardTemplate(
    val id: String,
    val name: String,
    val region: Region,
    val type: CardType,
    val cost: Int,
    val basePower: Int = 0,
    val baseHealth: Int = 0,
    val keywords: Set<Keyword> = emptySet(),
    val impactStacks: Int = 0,
    val spiritStacks: Int = 0,
    val spellSpeed: SpellSpeed = SpellSpeed.NONE,
    val description: String,
    val effectType: CardEffectType = CardEffectType.NONE,
    val effectValue: Int = 0,
    val targeting: CardTargetingSpec = CardTargetingSpec(),
    val isChampion: Boolean = false,
    val levelUpRequirementText: String = "",
    val levelUpPowerBonus: Int = 1,
    val levelUpHealthBonus: Int = 1,
    val countdownRounds: Int = 0,
    val isCollectibleReward: Boolean = true,
    val rarity: Rarity = Rarity.COMMON,
    val supportPowerBonus: Int = 0,
    val supportHealthBonus: Int = 0,
    val supportKeyword: Keyword? = null,
    val requiresAllySacrificeOnPlay: Boolean = false,
    val isDragon: Boolean = false,
    val isPoro: Boolean = false,
    val isDarkin: Boolean = false
)

data class DeckCardEntry(
    val cardId: String,
    val copies: Int = 2,
    val attachedItemIds: List<String> = emptyList()
)

data class HandCardInstance(
    val instanceId: String,
    val template: CardTemplate,
    val attachedItems: List<ItemTemplate> = emptyList(),
    val effectiveCost: Int,
    val bonusPower: Int = 0,
    val bonusHealth: Int = 0,
    val extraKeywords: Set<Keyword> = emptySet(),
    val isFleeting: Boolean = false,
    val isCreated: Boolean = false,
    val hasChimeBoon: Boolean = false,
    val hasPortalBoon: Boolean = false,
    val hasPuffcapTrap: Int = 0,
    val linkedShackledUnitId: String? = null,
    val transformedFromCard: HandCardInstance? = null
)

data class UnitOnBoard(
    val instanceId: String,
    val template: CardTemplate,
    val ownerIsPlayer: Boolean,
    val power: Int,
    val health: Int,
    val maxHealth: Int,
    val keywords: Set<Keyword>,
    val impactStacks: Int = 0,
    val spiritStacks: Int = 0,
    val gloomStacks: Int = 0,
    val hasBarrier: Boolean = false,
    val hasSpellShield: Boolean = false,
    val isStunned: Boolean = false,
    val isFrostbitten: Boolean = false,
    val frostbiteReduction: Int? = null,
    val isSilenced: Boolean = false,
    val isShackled: Boolean = false,
    val hasDeathless: Boolean = false,
    val fatedUsedThisRound: Boolean = false,
    val isLeveledUp: Boolean = false,
    val levelProgress: Int = 0,
    val attachedItems: List<ItemTemplate> = emptyList(),
    val equippedGear: CardTemplate? = null,
    val equippedGearName: String? = equippedGear?.name,
    val countdownRemaining: Int = 0,
    val hasEnlightenedBonusApplied: Boolean = false,
    val hasDeepBonusApplied: Boolean = false,
    val hasEvolveBonusApplied: Boolean = false,
    val supportPowerBonus: Int = template.supportPowerBonus,
    val supportHealthBonus: Int = template.supportHealthBonus,
    val supportKeyword: Keyword? = template.supportKeyword,
    val capturedUnits: List<UnitOnBoard> = emptyList(),
    val diedAtLevel: Int? = null,
    val killCount: Int = 0
) {
    val effectivePower: Int
        get() = when {
            frostbiteReduction != null -> maxOf(0, power - gloomStacks - frostbiteReduction)
            isFrostbitten -> 0
            else -> maxOf(0, power - gloomStacks)
        }

    val strikePower: Int
        get() = if (keywords.contains(Keyword.FORMIDABLE)) maxOf(0, health) else effectivePower

    val canAttack: Boolean
        get() = template.type != CardType.LANDMARK &&
            !isStunned &&
            !isShackled &&
            !keywords.contains(Keyword.IMMOBILE)

    val canBlock: Boolean
        get() = template.type != CardType.LANDMARK &&
            !isStunned &&
            !isShackled &&
            !keywords.contains(Keyword.IMMOBILE) &&
            !keywords.contains(Keyword.CANT_BLOCK)

    fun canBlockAttacker(attacker: UnitOnBoard): Boolean {
        if (!canBlock) return false
        if (attacker.keywords.contains(Keyword.ELUSIVE) &&
            !keywords.contains(Keyword.ELUSIVE) &&
            !keywords.contains(Keyword.SHARPSIGHT)
        ) return false
        if (attacker.keywords.contains(Keyword.FEARSOME) && effectivePower < 3) return false
        if (attacker.keywords.contains(Keyword.BRASH) && health < 3) return false
        return true
    }
}
