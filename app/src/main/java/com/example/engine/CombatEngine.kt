package com.example.engine

import com.example.model.AdventureConfig
import com.example.model.CardCatalog
import com.example.model.CardEffectType
import com.example.model.CardTemplate
import com.example.model.CardType
import com.example.model.ChampionLoadout
import com.example.model.EffectScope
import com.example.model.HandCardInstance
import com.example.model.ItemTemplate
import com.example.model.Keyword
import com.example.model.MapNode
import com.example.model.NodeType
import com.example.model.PowerTemplate
import com.example.model.Region
import com.example.model.RelicTemplate
import com.example.model.RunState
import com.example.model.SpellSpeed
import com.example.model.TargetSide
import com.example.model.TargetType
import com.example.model.UnitOnBoard
import java.util.UUID
import kotlin.random.Random

object CombatEngine {

    internal const val EMPTY_DECK_FATIGUE_DAMAGE = 2

    fun canPlayCard(state: CombatState, card: HandCardInstance): Boolean =
        canPlayCard(
            state = state,
            card = card.template,
            isPlayer = true,
            effectiveCost = state.effectivePlayCost(card)
        )

    fun canPlayCard(
        state: CombatState,
        card: CardTemplate,
        isPlayer: Boolean,
        effectiveCost: Int = card.cost
    ): Boolean {
        val expectedPriority = if (isPlayer) PriorityHolder.PLAYER else PriorityHolder.ENEMY
        if (state.priorityHolder != expectedPriority) return false
        if (isPlayer && (state.pendingTransformChoice != null || state.pendingHandShuffleChoice != null)) return false
        if (state.phase == CombatPhase.GAME_OVER || state.phase == CombatPhase.COMBAT_RESOLUTION_PHASE) return false

        val type = card.type
        val speed = card.spellSpeed

        // Check mana (Regular Mana capped at 10, Spell Mana capped at 3)
        val clampedCost = maxOf(0, effectiveCost)
        val isSpellLike = type == CardType.SPELL || type == CardType.CURSE
        val payWithHealth = isPlayer && PowerResolver.shouldPayFirstCardWithHealth(state)
        if (payWithHealth) {
            if (clampedCost > 0 && state.playerNexusHp <= clampedCost) return false
        } else {
            val available = if (isPlayer) {
                val regular = state.playerMana.coerceIn(0, 10)
                val spell = state.playerSpellMana.coerceIn(0, 3)
                if (isSpellLike) regular + spell else regular
            } else {
                val regular = state.enemyMana.coerceIn(0, 10)
                val spell = state.enemySpellMana.coerceIn(0, 3)
                if (isSpellLike) regular + spell else regular
            }
            if (available < clampedCost) return false
        }

        // Check board space for units/landmarks
        val board = if (isPlayer) state.playerBoard else state.enemyBoard
        val oppBoard = if (isPlayer) state.enemyBoard else state.playerBoard
        if ((type == CardType.UNIT || type == CardType.CHAMPION || type == CardType.LANDMARK) &&
            board.size >= 6
        ) return false
        if (card.requiresAllySacrificeOnPlay && board.none { it.template.type != CardType.LANDMARK }) {
            return false
        }
        if (card.effectType == CardEffectType.SACRIFICE_DAMAGE &&
            (board.none { it.template.type != CardType.LANDMARK } || oppBoard.none { it.template.type != CardType.LANDMARK })
        ) {
            return false
        }
        if (card.effectType == CardEffectType.CALL_TO_ARMS_EFFECT && isPlayer && state.playerHand.size <= 1) {
            return false
        }
        if (card.effectType == CardEffectType.POSSESS_UNIT &&
            (oppBoard.none { it.template.type != CardType.LANDMARK } || board.size >= 6)
        ) {
            return false
        }

        // Equipment requires at least 1 unit on board
        if (type == CardType.EQUIPMENT && board.isEmpty()) return false

        // Check timing windows per Spell Speed & Combat Spec (Phần 1/3)
        val fastUsedInCombat = if (isPlayer) state.playerFastSpellUsedInCombat else state.enemyFastSpellUsedInCombat
        return when (state.phase) {
            CombatPhase.MAIN_ACTION_PHASE -> when (speed) {
                SpellSpeed.BURST -> true
                SpellSpeed.FAST -> true
                SpellSpeed.FOCUS, SpellSpeed.SLOW, SpellSpeed.NONE, SpellSpeed.SKILL -> state.spellStack.isEmpty()
            }
            CombatPhase.SPELL_STACK_PHASE -> {
                speed == SpellSpeed.BURST || speed == SpellSpeed.FAST
            }
            CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            CombatPhase.COMBAT_DECLARE_BLOCK_PHASE -> when (speed) {
                SpellSpeed.BURST -> true
                SpellSpeed.FAST -> !fastUsedInCombat
                else -> false
            }
            CombatPhase.COMBAT_RESOLUTION_PHASE,
            CombatPhase.GAME_OVER -> false
        }
    }

    private fun nextId(): String = UUID.randomUUID().toString().take(8)

    /**
     * Computes regular mana for a given turn per Mana System Game Rules:
     * - Base Mana = turn (1..10)
     * - Champion Power 2 adds +1 regular mana every turn; Power 5 adds +1 regular mana every turn (stacks)
     * - Total Regular Mana is strictly capped at 10 (Spell Mana is tracked separately, max 3).
     */
    fun computeTurnRegularMana(
        turn: Int,
        hasPower2: Boolean,
        hasPower5: Boolean,
        bonusManaGems: Int = 0
    ): Int {
        val baseMana = turn.coerceIn(1, 10)
        val milestoneMana = (if (hasPower2) 1 else 0) + (if (hasPower5) 1 else 0)
        return (baseMana + milestoneMana + maxOf(0, bonusManaGems)).coerceIn(1, 10)
    }

    enum class ConstellationEffectPolarity {
        BUFF,   // Tăng chỉ số, hồi máu, tạo lá chắn, triệu hồi...
        DEBUFF  // Đóng băng, choáng, gây sát thương, giảm chỉ số, triệu hồi về tay...
    }

    /**
     * QUY TẮC SỨC MẠNH CHÒM SAO:
     * Mọi hiệu ứng Sức Mạnh Chòm Sao của người chơi nếu mang tính chất DEBUFF
     * tuyệt đối chỉ được phép chọn mục tiêu trong danh sách quân địch (enemyBoard),
     * không bao giờ tác động lên quân bản thân (playerBoard).
     */
    internal fun getConstellationTargets(
        state: CombatState,
        polarity: ConstellationEffectPolarity,
        restrictToIds: Set<String>? = null
    ): List<UnitOnBoard> {
        val pool = when (polarity) {
            ConstellationEffectPolarity.DEBUFF -> state.enemyBoard
            ConstellationEffectPolarity.BUFF -> state.playerBoard
        }
        return pool.filter { unit ->
            unit.template.type != CardType.LANDMARK &&
                (restrictToIds == null || restrictToIds.contains(unit.instanceId))
        }
    }

    /**
     * Kích hoạt các Sức Mạnh Chòm Sao khi kẻ địch tuyên bố tấn công.
     */
    internal fun applyEnemyAttackConstellationPowers(
        state: CombatState,
        enemyAttackerIds: List<String>,
        logs: MutableList<String>
    ): CombatState {
        var s = state

        // Anivia 6★: Khi kẻ địch tấn công, Đóng Băng kẻ địch mạnh nhất (chỉ tác động lên phe địch)
        if (s.championId == "champ_anivia" && s.starLevel >= 6) {
            val attackerSet = enemyAttackerIds.toSet()
            val attackingEnemies = getConstellationTargets(s, ConstellationEffectPolarity.DEBUFF, attackerSet)
            val enemyCandidates = attackingEnemies.ifEmpty {
                getConstellationTargets(s, ConstellationEffectPolarity.DEBUFF)
            }
            val strongestEnemy = enemyCandidates
                .filter { !it.isFrostbitten }
                .maxWithOrNull(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health })
                ?: enemyCandidates.maxWithOrNull(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health })

            if (strongestEnemy != null) {
                s = frostbiteUnit(
                    state = s,
                    unitId = strongestEnemy.instanceId,
                    isEnemy = true,
                    isSpell = false,
                    fromAllyEffect = false,
                    logs = logs
                )
                logs.add("Tường Băng (Anivia 6★): Đóng Băng kẻ địch mạnh nhất (${strongestEnemy.template.name})!")
            }
        }

        return s
    }

    /**
     * Kiểm tra bài phép Siêu Tốc gây sát thương trực tiếp lên Nhà Chính địch.
     * Những lá này được thi triển ngay lập tức mà không cần mở chế độ chọn mục tiêu.
     */
    fun isBurstDirectNexusDamageSpell(card: CardTemplate): Boolean =
        card.type == CardType.SPELL &&
            card.spellSpeed == SpellSpeed.BURST &&
            card.targeting.targetType == TargetType.NEXUS &&
            card.targeting.scope == EffectScope.ENEMY_NEXUS

    /**
     * Kiểm tra bài phép có bắt buộc người chơi chỉ định mục tiêu trước khi thi triển hay không.
     */
    fun requiresPlayerTarget(card: CardTemplate): Boolean {
        if (card.type != CardType.SPELL && card.type != CardType.CURSE) return false
        if (isBurstDirectNexusDamageSpell(card)) return false
        return card.targeting.requiresManualTarget
    }

    /**
     * Số lượng mục tiêu tối đa mà người chơi được chọn khi thi triển bài phép.
     */
    fun requiredPlayerTargetCount(card: CardTemplate): Int =
        if (requiresPlayerTarget(card)) card.targeting.targetCount else 0

    /**
     * Trả về cặp (enemyTargetIds, allyTargetIds) hợp lệ để người chơi chọn mục tiêu cho lá bài.
     */
    fun getValidSpellTargetIds(state: CombatState, card: CardTemplate): Pair<Set<String>, Set<String>> {
        val spec = card.targeting
        if (!spec.requiresManualTarget) return emptySet<String>() to emptySet()
        val enemyIds = state.enemyBoard
            .filter { it.template.type != CardType.LANDMARK }
            .map { it.instanceId }
            .toSet()
        val allyIds = state.playerBoard
            .filter {
                it.template.type != CardType.LANDMARK &&
                    (!spec.requireCanAttack || it.canAttack)
            }
            .map { it.instanceId }
            .toSet()
        return when (spec.targetSide) {
            TargetSide.ENEMY -> enemyIds to emptySet()
            TargetSide.ALLY -> emptySet<String>() to allyIds
            TargetSide.ANY, TargetSide.ALLY_AND_ENEMY -> enemyIds to allyIds
            TargetSide.NONE -> emptySet<String>() to emptySet()
        }
    }

    internal fun canonicalEnemyPowerKey(title: String): String = when (title) {
        "Ready Ranks", "Sẵn Sàng Đội Hình" -> "READY_RANKS"
        "Stone Ward", "Phòng Hộ Đá" -> "STONE_WARD"
        "Prismatic Shield", "Khiên Lăng Kính" -> "PRISMATIC_SHIELD"
        "For Demacia!", "Vì Demacia!" -> "FOR_DEMACIA"
        "Unseen Path", "Lối Đi Vô Hình" -> "UNSEEN_PATH"
        "Tempest Training", "Huấn Luyện Cuồng Phong" -> "TEMPEST_TRAINING"
        "Living Shadow", "Phân Thân Bóng Tối" -> "LIVING_SHADOW"
        "Enlightened Harmony", "Hòa Hợp Khai Sáng" -> "ENLIGHTENED_HARMONY"
        "Volatile Munitions", "Đạn Dược Bất Ổn" -> "VOLATILE_MUNITIONS"
        "Hextech Upgrade", "Nâng Cấp Hextech" -> "HEXTECH_UPGRADE"
        "Rocket Barrage", "Mưa Tên Lửa" -> "ROCKET_BARRAGE"
        "Glorious Evolution", "Tiến Hóa Huy Hoàng" -> "GLORIOUS_EVOLUTION"
        "Blazing Dawn", "Bình Minh Rực Cháy" -> "BLAZING_DAWN"
        "Undying Terror", "Nỗi Kinh Hoàng Bất Tử" -> "UNDYING_TERROR"
        "Endless Winter", "Mùa Đông Vô Tận" -> "ENDLESS_WINTER"
        "Star Forger", "Đấng Sáng Tạo Sao" -> "STAR_FORGER"
        "Thời Khắc Đối Đầu" -> "FIORA_SHOWDOWN"
        "Hoàng Đế Thống Trị" -> "VIEGO_DOMINION"
        else -> title
    }

    fun createEncounter(
        runState: RunState,
        loadout: ChampionLoadout,
        adventure: AdventureConfig,
        node: MapNode
    ): CombatState {
        val powers = runState.activePowerIds.mapNotNull { id ->
            CardCatalog.findPowerOrNull(id) ?: run { CardCatalog.getPower(id); null }
        }
        val relics = loadout.equippedRelics.mapNotNull { id ->
            id?.let { CardCatalog.findRelicOrNull(it) ?: run { CardCatalog.getRelic(it); null } }
        }

        // Expand RunState.runDeck into individual HandCardInstances
        val expandedDeck = mutableListOf<HandCardInstance>()
        runState.runDeck.forEach { entry ->
            val tmpl = CardCatalog.findCardOrNull(entry.cardId) ?: run {
                CardCatalog.getCard(entry.cardId)
                return@forEach
            }
            val items = entry.attachedItemIds.mapNotNull { itemId ->
                CardCatalog.findItemOrNull(itemId) ?: run { CardCatalog.getItem(itemId); null }
            }
            val costRed = items.sumOf { it.costReduction }
            val bonusPow = items.sumOf { it.bonusPower }
            val bonusHp = items.sumOf { it.bonusHealth }
            val itemKeywords = items.mapNotNull { it.grantedKeyword }.toSet()

            repeat(entry.copies) {
                expandedDeck.add(
                    HandCardInstance(
                        instanceId = "hc_${nextId()}",
                        template = tmpl,
                        attachedItems = items,
                        effectiveCost = maxOf(0, tmpl.cost - costRed),
                        bonusPower = bonusPow,
                        bonusHealth = bonusHp,
                        extraKeywords = itemKeywords,
                        isFleeting = tmpl.keywords.contains(Keyword.FLEETING)
                    )
                )
            }
        }
        val copyRelicsCount = relics.sumOf {
            when (it.id) {
                "R0028" -> 2
                "R0061" -> 7
                else -> 0
            }
        }
        if (copyRelicsCount > 0) {
            val champTmpl = CardCatalog.findCardOrNull(
                CardCatalog.findChampionOrNull(loadout.championId)?.cardId ?: ""
            )
            if (champTmpl != null) {
                repeat(copyRelicsCount) {
                    expandedDeck.add(createHandInstance(champTmpl, isCreated = true))
                }
            }
        }
        expandedDeck.shuffle()

        // Farsight Alteration: pull Farsight cards to top so they are drawn in opening hand
        val farsights = expandedDeck.filter { c -> c.attachedItems.any { it.farsightGameStartDraw } }
        val others = expandedDeck.filterNot { c -> c.attachedItems.any { it.farsightGameStartDraw } }
        val orderedDeck = farsights + others

        // Pipeline 1.2: Rút ngẫu nhiên 6 lá khởi đầu, bắt buộc có ít nhất 1 Anh Hùng nếu bộ bài có Anh Hùng
        val openingHand = orderedDeck.take(6).toMutableList()
        val remainingDeck = orderedDeck.drop(6).toMutableList()
        if (openingHand.none { it.template.isChampion }) {
            val champIdx = remainingDeck.indexOfFirst { it.template.isChampion }
            if (champIdx >= 0 && openingHand.isNotEmpty()) {
                val champCard = remainingDeck.removeAt(champIdx)
                val swapOutIdx = openingHand.indexOfLast { c -> c.attachedItems.none { it.farsightGameStartDraw } }
                    .takeIf { it >= 0 } ?: (openingHand.size - 1)
                val swappedOut = openingHand.removeAt(swapOutIdx)
                openingHand.add(champCard)
                remainingDeck.add(swappedOut)
                remainingDeck.shuffle()
            }
        }

        val champSpecificManaBonus = relics.sumOf { relic ->
            when (relic.id) {
                "R0098" -> if (loadout.championId == "champ_lux") 1 else 0
                "R0103" -> if (loadout.championId == "champ_yasuo") 1 else 0
                else -> 0
            }
        }
        val startingMana = if (loadout.starLevel >= 6) {
            3
        } else {
            (1 +
                loadout.startingManaBonus +
                relics.sumOf { it.startingManaBonus } +
                champSpecificManaBonus).coerceIn(1, 3)
        }

        val startingSpellMana = PowerResolver.startingSpellMana(powers)
        val celestialStartMana = if (adventure.mutators.any { it.id == "mut_celestial_might" }) 4 else 1
        val enemyStartMana = maxOf(1, node.enemyStartingMana, celestialStartMana).coerceIn(1, 10)

        val enemyPool = node.enemyDeckCardIds.mapNotNull { id ->
            CardCatalog.findCardOrNull(id) ?: run { CardCatalog.getCard(id); null }
        }.ifEmpty { listOf(CardCatalog.getCard("card_cithria")) }
        val openingEnemyHand = List(4) { enemyPool.random() }

        val initialLog = mutableListOf(
            "Chạm trán: ${node.title} vs ${node.enemyName}",
            "Năng Lượng Khởi Điểm: Bạn $startingMana | Địch $enemyStartMana"
        )
        if (adventure.mutators.any { it.id == "mut_celestial_might" }) {
            initialLog.add("Hiệu Ứng [Hiểm Họa Vũ Trụ]: Địch khởi đầu với $enemyStartMana Năng Lượng!")
        }
        if (node.enemyPowerTitle.isNotBlank()) {
            initialLog.add("Sức Mạnh Địch [${node.enemyPowerTitle}]: ${node.enemyPowerDescription}")
        }

        val enemyPowerKey = canonicalEnemyPowerKey(node.enemyPowerTitle)
        val effectiveEnemyMaxHp = node.enemyMaxHp + (if (enemyPowerKey == "STONE_WARD") 5 else 0)

        val deckRegions = orderedDeck.map { it.template.region }.toSet()
        var state = CombatState(
            encounterNode = node,
            adventure = adventure,
            championId = loadout.championId,
            starLevel = loadout.starLevel,
            equippedRelics = relics,
            activePowers = powers,
            mutators = adventure.mutators,
            round = 1,
            playerNexusHp = runState.currentNexusHp,
            playerMaxHp = runState.maxNexusHp,
            enemyNexusHp = effectiveEnemyMaxHp,
            enemyMaxHp = effectiveEnemyMaxHp,
            playerMana = startingMana,
            playerMaxMana = startingMana,
            playerSpellMana = startingSpellMana,
            enemyMana = enemyStartMana,
            enemyMaxMana = enemyStartMana,
            enemySpellMana = 0,
            playerHasAttackToken = true,
            enemyHasAttackToken = false,
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 0,
            playerHand = openingHand,
            playerDrawPile = remainingDeck,
            enemyHand = openingEnemyHand,
            enemyDrawPool = enemyPool,
            startingDeckSize = orderedDeck.size,
            deckRegions = deckRegions,
            combatLog = initialLog
        )

        // Starting Boss Champions on Board at Game Start (Chí Tử applies on summon)
        if (enemyPowerKey == "FIORA_SHOWDOWN") {
            val startLogs = state.combatLog.toMutableList()
            state = summonEnemyUnitFromPool(state, CardCatalog.getCard("card_fiora"), spendMana = 0, logs = startLogs)
            state = state.copy(combatLog = startLogs.takeLast(16))
        } else if (enemyPowerKey == "VIEGO_DOMINION") {
            val startLogs = state.combatLog.toMutableList()
            state = summonEnemyUnitFromPool(state, CardCatalog.getCard("card_viego"), spendMana = 0, logs = startLogs)
            state = state.copy(combatLog = startLogs.takeLast(16))
        }

        // Apply Game Start / Round 1 Star Powers & Powers
        state = applyRoundStartHooks(state, isGameStart = true)
        return syncChampionSpellsInHand(state)
    }

    internal fun applyRoundStartHooks(state: CombatState, isGameStart: Boolean): CombatState {
        var s = state
        val logs = s.combatLog.toMutableList()

        // Mutator: Spirit Breeze
        if (s.mutators.any { it.id == "mut_spirit_breeze" }) {
            s = s.copy(
                playerSpellMana = minOf(3, s.playerSpellMana + 1),
                enemySpellMana = minOf(3, s.enemySpellMana + 1)
            )
        }

        // Mutator: Dư Âm Ác Mộng
        if (s.encounterNode.type == NodeType.FINAL_BOSS &&
            s.mutators.any { it.id == "mut_nightmare_curse" } &&
            s.playerHand.size < 10 &&
            (isGameStart || (s.round % 2 == 0 && canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle) != "STAR_FORGER"))
        ) {
            s = s.copy(
                playerHand = s.playerHand + createHandInstance(
                    CardCatalog.getCard("card_curse_suppression"),
                    isCreated = false
                )
            )
            logs.add("Dư Âm Ác Mộng yểm Lời Nguyền Áp Chế lên tay bạn!")
        }

        s = PowerResolver.onRoundStart(s, isGameStart = isGameStart, logs = logs)
        // Đài Quan Sát Hextech (card_hextech_observatory): Nếu có Lượt Tấn Công, rút 1 lá
        val observatoryCount = s.playerBoard.count { it.template.id == "card_hextech_observatory" && !it.isSilenced }
        if (observatoryCount > 0 && s.playerHasAttackToken) {
            repeat(observatoryCount) { s = drawPlayerCard(s) }
            logs.add("Đài Quan Sát Hextech rút $observatoryCount lá bài!")
        }

        // Pipeline 1.1: Kiểm tra Khai Sáng DUY NHẤT tại Đầu Vòng Đấu sau khi nạp lại mana (regularMana >= 10)
        s = s.copy(isEnlightened = s.playerMana >= 10)

        // Sức Mạnh Sao Của Tướng
        when (s.championId) {
            "champ_jinx" -> {
                if (isGameStart && s.starLevel >= 2 && s.playerHand.size < 10) {
                    val rocket = createHandInstance(CardCatalog.getCard("card_mystic_shot"), isFleeting = true)
                    s = s.copy(playerHand = s.playerHand + rocket)
                    logs.add("Jinx 2★ tạo Phát Bắn Thần Bí Chóng Tàn.")
                }
            }
            "champ_lux" -> {
                if (s.starLevel >= 2) {
                    s = s.copy(playerSpellMana = minOf(3, s.playerSpellMana + 1))
                }
                if (s.starLevel >= 6) {
                    s = s.copy(
                        playerHasAttackToken = true,
                        playerBoard = s.playerBoard.map { it.copy(hasBarrier = true) }
                    )
                    logs.add("Lux 6★ Tập Hợp & ban Lá Chắn cho đồng minh!")
                }
            }
            "champ_yasuo" -> {
                val debuffEnemies = getConstellationTargets(s, ConstellationEffectPolarity.DEBUFF)
                if (s.starLevel >= 1 && debuffEnemies.isNotEmpty()) {
                    val target = if (s.starLevel >= 3) {
                        debuffEnemies.maxByOrNull { it.effectivePower }
                    } else {
                        debuffEnemies.minByOrNull { it.effectivePower }
                    }
                    if (target != null) {
                        s = stunEnemyUnit(s, target.instanceId, logs)
                    }
                }
                if (s.starLevel >= 3 && s.playerHand.size < 10) {
                    val tempest = createHandInstance(CardCatalog.getCard("card_steel_tempest"), isFleeting = true)
                    s = s.copy(playerHand = s.playerHand + tempest)
                }
            }
            "champ_garen" -> {
                if (s.starLevel >= 6 || s.playerBoard.any { it.template.id == "card_garen" && it.isLeveledUp && !it.isSilenced }) {
                    s = s.copy(playerHasAttackToken = true)
                    if (!isGameStart && s.playerBoard.any { it.template.id == "card_garen" && it.isLeveledUp && !it.isSilenced }) {
                        logs.add("Garen Thăng Cấp kích hoạt Tập Hợp!")
                    }
                }
            }
            "champ_ashe" -> {
                val debuffEnemies = getConstellationTargets(s, ConstellationEffectPolarity.DEBUFF)
                if (s.starLevel >= 1 && debuffEnemies.isNotEmpty()) {
                    if (s.starLevel >= 6) {
                        val newlyFrostbitten = debuffEnemies.count { !it.isFrostbitten }
                        val debuffIds = debuffEnemies.map { it.instanceId }.toSet()
                        s = s.copy(
                            enemyBoard = s.enemyBoard.map {
                                if (debuffIds.contains(it.instanceId)) it.copy(isFrostbitten = true) else it
                            },
                            enemiesFrostbittenCount = s.enemiesFrostbittenCount + newlyFrostbitten
                        )
                        logs.add("Ashe 6★ Đóng Băng TẤT CẢ kẻ địch!")
                    } else {
                        val strongest = debuffEnemies.filter { !it.isFrostbitten }.maxByOrNull { it.effectivePower }
                            ?: debuffEnemies.maxByOrNull { it.effectivePower }
                        if (strongest != null) {
                            val newlyFrostbitten = if (!strongest.isFrostbitten) 1 else 0
                            s = s.copy(
                                enemyBoard = s.enemyBoard.map {
                                    if (it.instanceId == strongest.instanceId) it.copy(isFrostbitten = true) else it
                                },
                                enemiesFrostbittenCount = s.enemiesFrostbittenCount + newlyFrostbitten
                            )
                            logs.add("Sức Mạnh Sao Ashe Đóng Băng ${strongest.template.name}.")
                        }
                    }
                }
                if (s.starLevel >= 3) {
                    s.playerBoard.maxByOrNull { it.effectivePower }?.let { topAlly ->
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == topAlly.instanceId) {
                                it.copy(power = it.power + 2, health = it.health + 2, maxHealth = it.maxHealth + 2)
                            } else it
                        })
                        logs.add("Ashe 3★ ban +2|+2 cho ${topAlly.template.name}!")
                    }
                }
            }
            "champ_anivia" -> {
                if (isGameStart && s.starLevel >= 2) {
                    val bonusTotal = s.championMilestoneManaBonus
                    logs.add("Anivia ${s.starLevel}★ kích hoạt +$bonusTotal Mana Thường khởi đầu mỗi lượt.")
                }
            }
            "champ_tryndamere" -> {
                if (isGameStart && s.starLevel >= 2) {
                    val bonusTotal = s.championMilestoneManaBonus
                    logs.add("Tryndamere ${s.starLevel}★ kích hoạt +$bonusTotal Mana Thường khởi đầu mỗi lượt.")
                }
                if (s.starLevel >= 3 || (s.starLevel >= 1 && s.playerHasAttackToken)) {
                    val existingIdx = s.playerHand.indexOfFirst { it.template.id == "card_call_to_arms" }
                    if (existingIdx >= 0) {
                        s = s.copy(playerHand = s.playerHand.mapIndexed { idx, c ->
                            if (idx == existingIdx) c.copy(effectiveCost = maxOf(0, c.effectiveCost - 2)) else c
                        })
                        logs.add("Lời Thề Chiến Tướng: Giảm 2 tiêu hao cho Lệnh Nhập Ngũ trên tay!")
                    } else if (s.playerHand.size < 10) {
                        s = s.copy(playerHand = s.playerHand + createHandInstance(CardCatalog.getCard("card_call_to_arms"), isCreated = true))
                        logs.add("Lời Thề Chiến Tướng: Tạo 1 lá Lệnh Nhập Ngũ trên tay!")
                    }
                }
            }
        }

        // Garen Thăng Cấp
        if (s.championId != "champ_garen" && s.playerBoard.any { it.template.id == "card_garen" && it.isLeveledUp && !it.isSilenced }) {
            s = s.copy(playerHasAttackToken = true)
            logs.add("Garen Thăng Cấp kích hoạt Tập Hợp!")
        }

        // Người Máy Đạn Đạo
        if (!isGameStart && s.playerBoard.any { it.template.id == "card_ballistic_bot" && !it.isSilenced } && s.playerHand.size < 10) {
            val ignition = createHandInstance(CardCatalog.getCard("card_ignition"), isFleeting = true)
            s = s.copy(playerHand = s.playerHand + ignition)
            logs.add("Người Máy Đạn Đạo tạo Tia Lửa Chóng Tàn.")
        }

        // Game Start Relic: Swain's Raven Army (Drain 5 from enemy Nexus)
        if (isGameStart) {
            val drainCount = s.equippedRelics.count { it.id == "R0104" }
            if (drainCount > 0) {
                val drainAmt = 5 * drainCount
                s = dealDamageToEnemyNexus(s, drainAmt, logs)
                val healed = minOf(s.playerMaxHp, s.playerNexusHp + drainAmt)
                s = s.copy(playerNexusHp = healed)
                logs.add("Swain's Raven Army hút $drainAmt Máu từ Nhà Chính địch!")
            }
        }

        // Relic: Archangel's Staff (Round Start refill spell mana if champion on board)
        if (s.playerBoard.any { u -> u.template.isChampion } &&
            s.equippedRelics.any { it.refillSpellManaRoundStart }
        ) {
            s = s.copy(playerSpellMana = 3)
        }

        // Relic: The Gravedigger's Spade (Round Start draw 1 Fleeting)
        if (!isGameStart && s.equippedRelics.any { it.id == "R0014" } && s.playerHand.size < 10) {
            val beforeSize = s.playerHand.size
            s = drawPlayerCard(s)
            if (s.playerHand.size > beforeSize) {
                val drawn = s.playerHand.last().copy(isFleeting = true)
                s = s.copy(playerHand = s.playerHand.dropLast(1) + drawn)
                logs.add("The Gravedigger's Spade rút 1 lá Phù Du.")
            }
        }

        // Relic: Ranger's Command (Round Start if attack token, grant random ally Scout)
        if (s.playerHasAttackToken && s.equippedRelics.any { it.id == "R0164" } && s.playerBoard.isNotEmpty()) {
            val candidate = s.playerBoard.filter { !it.keywords.contains(Keyword.SCOUT) }.randomOrNull()
            if (candidate != null) {
                s = s.copy(playerBoard = s.playerBoard.map { u ->
                    if (u.instanceId == candidate.instanceId) u.copy(keywords = u.keywords + Keyword.SCOUT) else u
                })
                logs.add("Ranger's Command ban Trinh Sát cho ${candidate.template.name}.")
            }
        }

        // Relic: Siphoned Sorrow (Round Start steal up to 3 Health from random enemy)
        if (!isGameStart &&
            s.playerBoard.any { it.template.isChampion } &&
            s.equippedRelics.any { it.id == "R0138" } &&
            s.enemyBoard.any { it.health > 1 }
        ) {
            val victim = s.enemyBoard.filter { it.health > 1 }.random()
            val stolen = minOf(3, victim.health - 1)
            val champ = s.playerBoard.firstOrNull { it.template.isChampion }
            if (stolen > 0 && champ != null) {
                s = s.copy(
                    enemyBoard = s.enemyBoard.map { if (it.instanceId == victim.instanceId) it.copy(health = it.health - stolen) else it },
                    playerBoard = s.playerBoard.map {
                        if (it.instanceId == champ.instanceId) it.copy(health = it.health + stolen, maxHealth = it.maxHealth + stolen) else it
                    }
                )
                logs.add("Siphoned Sorrow hút $stolen Máu từ ${victim.template.name}!")
            }
        }

        // Relic: The Grand General's Counterplan
        if (!isGameStart &&
            s.playerBoard.any { it.template.isChampion } &&
            s.equippedRelics.any { it.createChampionSpellRoundStart } &&
            s.playerHand.size < 10
        ) {
            val spellId = when (s.championId) {
                "champ_jinx" -> "card_get_excited"
                "champ_lux" -> "card_prismatic_barrier"
                "champ_yasuo" -> "card_steel_tempest"
                "champ_garen" -> "card_single_combat"
                "champ_anivia" -> "card_anivia_harsh_winds"
                "champ_tryndamere" -> "card_tryndamere_battle_fury"
                else -> "card_flash_freeze"
            }
            s = s.copy(playerHand = s.playerHand + createHandInstance(CardCatalog.getCard(spellId), isFleeting = true))
        }

        // Sức Mạnh Địch Đầu Vòng Đấu
        if (!isGameStart) {
            when (canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle)) {
                "FOR_DEMACIA" -> {
                    if (s.round % 2 == 0 && s.enemyBoard.isNotEmpty()) {
                        s = s.copy(enemyBoard = s.enemyBoard.map {
                            it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                        })
                        logs.add("Vì Demacia! Quân địch nhận +1|+1.")
                    }
                }
                "TEMPEST_TRAINING" -> {
                    if (s.round % 2 == 0) {
                        s.playerBoard.minByOrNull { it.effectivePower }?.let { weakest ->
                            s = s.copy(playerBoard = s.playerBoard.map {
                                if (it.instanceId == weakest.instanceId) {
                                    it.copy(isStunned = true, keywords = it.keywords + Keyword.STUN)
                                } else it
                            })
                            logs.add("Huấn Luyện Cuồng Phong làm Choáng ${weakest.template.name} của bạn!")
                        }
                    }
                }
                "PRISMATIC_SHIELD" -> {
                    val topEnemy = s.enemyBoard.maxByOrNull { it.effectivePower }
                    if (topEnemy != null) {
                        s = s.copy(enemyBoard = s.enemyBoard.map {
                            if (it.instanceId == topEnemy.instanceId) it.copy(hasBarrier = true) else it
                        })
                        logs.add("Lux Trùm Phụ ban Lá Chắn cho ${topEnemy.template.name}.")
                    }
                }
                "ROCKET_BARRAGE" -> {
                    s = dealDamageToPlayerNexus(s, 1, logs)
                }
                "ENDLESS_WINTER" -> {
                    val topAlly = s.playerBoard.maxByOrNull { it.effectivePower }
                    if (topAlly != null) {
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == topAlly.instanceId) it.copy(isFrostbitten = true) else it
                        })
                        logs.add("Ashe Trùm Phụ Đóng Băng ${topAlly.template.name}!")
                    }
                }
                "STAR_FORGER" -> {
                    s = s.copy(enemyBoard = s.enemyBoard.map {
                        it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                    })
                    if (s.round % 2 == 0 && s.playerHand.size < 10) {
                        s = s.copy(
                            playerHand = s.playerHand + createHandInstance(
                                CardCatalog.getCard("card_curse_suppression"),
                                isCreated = false
                            )
                        )
                        logs.add("Aurelion Sol yểm Lời Nguyền Áp Chế lên tay bạn!")
                    }
                }
                "VIEGO_DOMINION" -> {
                    s.playerStrongestFollowerDiedLastRound?.let { deadFollower ->
                        if (s.enemyBoard.size < 6) {
                            s = summonEnemyUnitFromPool(
                                state = s,
                                chosen = deadFollower.template,
                                spendMana = 0,
                                logs = logs,
                                forceEphemeral = true
                            )
                            logs.add("Hoàng Đế Thống Trị triệu hồi bản sao Cảm Tử của ${deadFollower.template.name}!")
                        }
                    }
                    s = s.copy(playerStrongestFollowerDiedLastRound = null)
                }
            }

            // Mutator: Sương Biến Ảo (Đầu Vòng Đấu nếu Địch đang giữ Attack Token)
            if (s.mutators.any { it.id == "mut_shadow_mist" } && s.enemyHasAttackToken) {
                s = s.copy(
                    enemyMistStatBonus = s.enemyMistStatBonus + 1,
                    enemyBoard = s.enemyBoard.map { u ->
                        if (u.template.id == "card_encroaching_mist") {
                            u.copy(power = u.power + 1, health = u.health + 1, maxHealth = u.maxHealth + 1)
                        } else u
                    }
                )
                logs.add("Sương Biến Ảo ban +1|+1 cho mọi Màn Sương Xâm Chiếm ở mọi nơi!")
                if (s.enemyBoard.size < 6) {
                    s = summonEnemyUnitFromPool(
                        state = s,
                        chosen = CardCatalog.getCard("card_encroaching_mist"),
                        spendMana = 0,
                        logs = logs
                    )
                }
            }

            // Viego (Lv2) Đầu Vòng Đấu: Chiếm vĩnh viễn kẻ địch mạnh nhất (hoặc tiêu diệt nếu là Anh Hùng)
            if (s.enemyBoard.any { it.template.id == "card_viego" && it.isLeveledUp && !it.isSilenced }) {
                val strongestTarget = s.playerBoard
                    .filter { it.template.type != CardType.LANDMARK }
                    .maxWithOrNull(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health }.thenBy { it.template.cost })
                if (strongestTarget != null) {
                    if (strongestTarget.template.isChampion) {
                        s = removeUnit(s, strongestTarget.instanceId, isEnemy = false, logs = logs)
                        logs.add("Viego (Lv2) tiêu diệt Anh Hùng ${strongestTarget.template.name}!")
                    } else if (s.enemyBoard.size < 6) {
                        val stolen = strongestTarget.copy(ownerIsPlayer = false)
                        s = s.copy(
                            playerBoard = s.playerBoard.filterNot { it.instanceId == strongestTarget.instanceId },
                            enemyBoard = s.enemyBoard + stolen,
                            tempPossessedReturns = s.tempPossessedReturns - strongestTarget.instanceId
                        )
                        s = cleanCombatUnitReferences(s, strongestTarget.instanceId)
                        logs.add("Viego (Lv2) biến ${strongestTarget.template.name} thành quân của bản thân vĩnh viễn!")
                    }
                }
            }
            if (s.playerBoard.any { it.template.id == "card_viego" && it.isLeveledUp && !it.isSilenced }) {
                val strongestTarget = s.enemyBoard
                    .filter { it.template.type != CardType.LANDMARK }
                    .maxWithOrNull(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health }.thenBy { it.template.cost })
                if (strongestTarget != null) {
                    if (strongestTarget.template.isChampion) {
                        s = removeUnit(s, strongestTarget.instanceId, isEnemy = true, logs = logs)
                        logs.add("Viego (Lv2) của bạn tiêu diệt Anh Hùng ${strongestTarget.template.name}!")
                    } else if (s.playerBoard.size < 6) {
                        val stolen = strongestTarget.copy(ownerIsPlayer = true)
                        s = s.copy(
                            enemyBoard = s.enemyBoard.filterNot { it.instanceId == strongestTarget.instanceId },
                            playerBoard = s.playerBoard + stolen,
                            tempPossessedReturns = s.tempPossessedReturns - strongestTarget.instanceId
                        )
                        s = cleanCombatUnitReferences(s, strongestTarget.instanceId)
                        logs.add("Viego (Lv2) của bạn chiếm vĩnh viễn ${strongestTarget.template.name}!")
                    }
                }
            }

            // Enemy Viktor Round Start creates Hex Core Upgrade (interceptable by P0756)
            if (s.enemyBoard.any { it.template.id == "card_viktor" && !it.isSilenced }) {
                s = addEnemyCreatedCardToHand(s, CardCatalog.getCard("card_hex_core_upgrade"), logs)
            }

            // Quái Cây (Invasive Hydravine) Đầu Vòng Đấu: Triệu hồi 1 Màn Sương Xâm Chiếm
            val enemyHydraCount = s.enemyBoard.count { it.template.id == "card_invasive_hydravine" && !it.isSilenced }
            repeat(enemyHydraCount) {
                if (s.enemyBoard.size < 6) {
                    s = summonEnemyUnitFromPool(s, CardCatalog.getCard("card_encroaching_mist"), spendMana = 0, logs = logs)
                }
            }
            val playerHydraCount = s.playerBoard.count { it.template.id == "card_invasive_hydravine" && !it.isSilenced }
            repeat(playerHydraCount) {
                if (s.playerBoard.size < 6) {
                    s = summonPlayerUnit(s, createHandInstance(CardCatalog.getCard("card_encroaching_mist")), logs).first
                }
            }
        }

        // Đếm Ngược Địa Danh
        val completedLandmarks = mutableListOf<UnitOnBoard>()
        val tickedBoard = mutableListOf<UnitOnBoard>()
        s.playerBoard.forEach { u ->
            if (u.template.type == CardType.LANDMARK && u.countdownRemaining > 0) {
                val rem = u.countdownRemaining - 1
                if (rem <= 0) {
                    completedLandmarks.add(u)
                } else {
                    tickedBoard.add(u.copy(countdownRemaining = rem))
                }
            } else {
                tickedBoard.add(u)
            }
        }
        s = s.copy(playerBoard = tickedBoard)
        completedLandmarks.forEach { lm ->
            s = completeCountdownLandmark(s, lm, logs)
        }
        s = s.copy(combatLog = logs.takeLast(16))
        s = applyDeepBoardBuffs(s, logs)
        s = refreshEvolveAndSeenKeywords(s, logs)
        s = checkChampionLevelUps(s, logs, isRoundStart = !isGameStart)

        // Hồi sinh Trứng Anivia vào Đầu Vòng Đấu khi đạt Khai Sáng (chỉ số gốc theo diedAtLevel, loại bỏ mọi buff cũ)
        if (!isGameStart && s.isEnlightened && s.playerBoard.any { it.template.id == "card_eggnivia" && !it.isSilenced }) {
            val aniviaTmpl = CardCatalog.getCard("card_anivia")
            s = s.copy(
                playerBoard = s.playerBoard.map { u ->
                    if (u.template.id == "card_eggnivia" && !u.isSilenced) {
                        val diedLvl = u.diedAtLevel ?: 2
                        val leveled = diedLvl >= 2
                        val newPow = if (leveled) aniviaTmpl.basePower + aniviaTmpl.levelUpPowerBonus else aniviaTmpl.basePower
                        val newHp = if (leveled) aniviaTmpl.baseHealth + aniviaTmpl.levelUpHealthBonus else aniviaTmpl.baseHealth
                        u.copy(
                            template = aniviaTmpl,
                            power = newPow,
                            health = newHp,
                            maxHealth = newHp,
                            keywords = aniviaTmpl.keywords + s.equippedRelics.flatMap { it.allGrantedKeywords },
                            isLeveledUp = leveled,
                            diedAtLevel = null,
                            hasBarrier = false,
                            isFrostbitten = false,
                            frostbiteReduction = null,
                            isStunned = false,
                            gloomStacks = 0,
                            spiritStacks = 0,
                            impactStacks = 0
                        )
                    } else u
                }
            )
            logs.add("Trứng Anivia hồi sinh thành Anivia!")
        }

        s = syncChampionSpellsInHand(s)
        return checkGameOver(s.copy(combatLog = logs.takeLast(16)))
    }

    /**
     * Pipeline 1.4: Tính toán sát thương động cho Kỹ năng và Bài phép (cộng dồn Relic + Power).
     */
    fun calculateFinalDamage(
        baseDamage: Int,
        card: CardTemplate? = null,
        state: CombatState,
        sourceIsPlayer: Boolean = true,
        extraBonus: Int = 0
    ): Int {
        if (baseDamage <= 0) return 0
        val relicBonus = if (sourceIsPlayer) {
            val baseRelic = state.equippedRelics.sumOf { it.spellSkillDamageBonus }
            val elementalRelic = if (card?.keywords?.contains(Keyword.ELEMENTAL) == true) {
                state.equippedRelics.sumOf { it.elementalSpellSkillDamageBonus }
            } else 0
            val whisperBonus = if (card?.spellSpeed == SpellSpeed.SKILL && state.equippedRelics.any { it.id == "R0137" }) {
                state.enemyBoard.count { it.isStunned }
            } else 0
            baseRelic + elementalRelic + whisperBonus
        } else 0
        val powerBonus = if (sourceIsPlayer) state.activePowers.sumOf { it.spellSkillDamageBonus } else 0
        val mult = if (sourceIsPlayer && state.championId == "champ_jinx" && state.starLevel >= 6) 2 else 1
        val deadlyMult = if (!sourceIsPlayer && state.mutators.any { it.id == "mut_deadly" }) 2 else 1
        return maxOf(0, (baseDamage + relicBonus + powerBonus + extraBonus) * mult * deadlyMult)
    }

    /**
     * Pipeline 1.3: Biến đổi bản sao Anh Hùng trên tay thành Bài Phép Anh Hùng khi có trên sân và hoàn nguyên khi rời sân.
     */
    internal fun syncChampionSpellsInHand(state: CombatState): CombatState {
        val activeChampionIds = state.playerBoard
            .filter { it.template.isChampion }
            .map { it.template.id }
            .toSet()
        var changed = false
        val updatedHand = state.playerHand.map { hc ->
            val champId = hc.template.id
            if (hc.template.isChampion && !hc.keepAsUnitInHand && activeChampionIds.contains(champId)) {
                val spellId = CardCatalog.championSpellIdFor(champId)
                val spellTmpl = spellId?.let { CardCatalog.findCardOrNull(it) }
                if (spellTmpl != null) {
                    changed = true
                    HandCardInstance(
                        instanceId = hc.instanceId,
                        template = spellTmpl,
                        attachedItems = hc.attachedItems,
                        effectiveCost = maxOf(0, spellTmpl.cost - hc.attachedItems.sumOf { it.costReduction }),
                        bonusPower = 0,
                        bonusHealth = 0,
                        extraKeywords = emptySet(),
                        isFleeting = hc.isFleeting || spellTmpl.keywords.contains(Keyword.FLEETING),
                        isCreated = hc.isCreated,
                        hasChimeBoon = hc.hasChimeBoon,
                        hasPortalBoon = hc.hasPortalBoon,
                        hasPuffcapTrap = hc.hasPuffcapTrap,
                        linkedShackledUnitId = hc.linkedShackledUnitId,
                        transformedFromCard = hc
                    )
                } else hc
            } else if (hc.transformedFromCard != null) {
                val origChampId = hc.transformedFromCard.template.id
                if (!activeChampionIds.contains(origChampId)) {
                    changed = true
                    hc.transformedFromCard.copy(
                        instanceId = hc.instanceId,
                        isFleeting = hc.isFleeting
                    )
                } else hc
            } else {
                hc
            }
        }
        return if (changed) state.copy(playerHand = updatedHand) else state
    }

    internal fun createHandInstance(
        template: CardTemplate,
        isFleeting: Boolean = false,
        isCreated: Boolean = true
    ): HandCardInstance {
        return HandCardInstance(
            instanceId = "hc_${nextId()}",
            template = template,
            effectiveCost = template.cost,
            isFleeting = isFleeting || template.keywords.contains(Keyword.FLEETING),
            isCreated = isCreated
        )
    }

    fun drawPlayerCard(state: CombatState): CombatState {
        if (state.playerDrawPile.isEmpty()) {
            val logs = state.combatLog.toMutableList()
            logs.add("Bộ bài của bạn đã hết! Kiệt sức gây $EMPTY_DECK_FATIGUE_DAMAGE sát thương lên Nhà Chính.")
            val afterFatigue = dealDamageToPlayerNexus(state, EMPTY_DECK_FATIGUE_DAMAGE, logs)
            return afterFatigue.copy(combatLog = logs.takeLast(16))
        }
        val topCard = state.playerDrawPile.first()
        val remainingPile = state.playerDrawPile.drop(1)
        val logs = state.combatLog.toMutableList()
        var s = state.copy(playerDrawPile = remainingPile)

        // Check Puffcap Traps
        if (topCard.hasPuffcapTrap > 0) {
            s = dealDamageToPlayerNexus(s, topCard.hasPuffcapTrap, logs)
            logs.add("Bẫy Nấm Độc gây ${topCard.hasPuffcapTrap} sát thương!")
        }

        // Check Chime Boons
        if (topCard.hasChimeBoon && s.playerHand.isNotEmpty()) {
            val idx = Random.nextInt(s.playerHand.size)
            val updatedHand = s.playerHand.toMutableList()
            val chosen = updatedHand[idx]
            updatedHand[idx] = chosen.copy(bonusPower = chosen.bonusPower + 1, bonusHealth = chosen.bonusHealth + 1)
            s = s.copy(playerHand = updatedHand)
            logs.add("Chuông Vũ Trụ ban +1|+1 cho ${chosen.template.name}!")
        }

        if (s.playerHand.size < 10) {
            s = s.copy(playerHand = s.playerHand + topCard, combatLog = logs.takeLast(16))
        } else {
            logs.add("Đầy tay! Tiêu hủy ${topCard.template.name}.")
            s = s.copy(combatLog = logs.takeLast(16))
        }
        s = applyDeepBoardBuffs(s, logs)
        s = syncChampionSpellsInHand(s)
        return s.copy(combatLog = logs.takeLast(16))
    }

    internal fun grantStatsToDeckUnits(
        state: CombatState,
        count: Int?,
        powerGain: Int,
        healthGain: Int,
        logs: MutableList<String>,
        sourceLabel: String
    ): CombatState {
        if (state.playerDrawPile.isEmpty() || (powerGain <= 0 && healthGain <= 0)) return state
        val warlordMult = if (state.championId == "champ_tryndamere" && state.equippedRelics.any { it.id == "R0131" }) 2 else 1
        val finalPow = powerGain * warlordMult
        val finalHp = healthGain * warlordMult

        var remaining = count ?: Int.MAX_VALUE
        var buffedCount = 0
        val updatedPile = state.playerDrawPile.map { hc ->
            val isUnitLike = hc.template.type == CardType.UNIT || hc.template.type == CardType.CHAMPION
            if (remaining > 0 && isUnitLike) {
                remaining--
                buffedCount++
                hc.copy(
                    bonusPower = hc.bonusPower + finalPow,
                    bonusHealth = hc.bonusHealth + finalHp
                )
            } else {
                hc
            }
        }
        if (buffedCount == 0) return state
        logs.add("$sourceLabel: Ban +$finalPow|+$finalHp cho $buffedCount đồng minh trong bộ bài.")
        return state.copy(playerDrawPile = updatedPile)
    }

    private fun applyPluckyPoroEmpowerment(unit: UnitOnBoard): UnitOnBoard {
        if (unit.template.id != "card_plucky_poro" || unit.isSilenced) return unit
        val hasStatBuff = unit.power > unit.template.basePower || unit.maxHealth > unit.template.baseHealth
        if (!hasStatBuff) return unit
        return unit.copy(
            keywords = unit.keywords + Keyword.REGENERATION + Keyword.OVERWHELM + Keyword.TOUGH
        )
    }

    private fun refreshPluckyPoroEmpowerment(state: CombatState): CombatState {
        val nextPlayer = state.playerBoard.map { applyPluckyPoroEmpowerment(it) }
        val nextEnemy = state.enemyBoard.map { applyPluckyPoroEmpowerment(it) }
        return if (nextPlayer == state.playerBoard && nextEnemy == state.enemyBoard) {
            state
        } else {
            state.copy(playerBoard = nextPlayer, enemyBoard = nextEnemy)
        }
    }

    private fun drawSpecificUnitFromDeck(
        state: CombatState,
        logs: MutableList<String>,
        predicate: (HandCardInstance) -> Boolean
    ): Pair<CombatState, HandCardInstance?> {
        val idx = state.playerDrawPile.indexOfFirst(predicate)
        if (idx < 0) return state to null
        val chosen = state.playerDrawPile[idx]
        val remainingPile = state.playerDrawPile.toMutableList().apply { removeAt(idx) }
        var s = state.copy(playerDrawPile = remainingPile)

        if (chosen.hasPuffcapTrap > 0) {
            s = dealDamageToPlayerNexus(s, chosen.hasPuffcapTrap, logs)
            logs.add("Bẫy Nấm Độc gây ${chosen.hasPuffcapTrap} sát thương!")
        }
        if (chosen.hasChimeBoon && s.playerHand.isNotEmpty()) {
            val handIdx = Random.nextInt(s.playerHand.size)
            val updatedHand = s.playerHand.toMutableList()
            val target = updatedHand[handIdx]
            updatedHand[handIdx] = target.copy(bonusPower = target.bonusPower + 1, bonusHealth = target.bonusHealth + 1)
            s = s.copy(playerHand = updatedHand)
            logs.add("Chuông Vũ Trụ ban +1|+1 cho ${target.template.name}!")
        }
        if (s.playerHand.size < 10) {
            s = s.copy(playerHand = s.playerHand + chosen)
        } else {
            logs.add("Đầy tay! Tiêu hủy ${chosen.template.name}.")
        }
        s = applyDeepBoardBuffs(s, logs)
        s = syncChampionSpellsInHand(s)
        return s to chosen
    }

    fun playCardFromHand(
        state: CombatState,
        handCardId: String,
        targetUnitId: String? = null
    ): CombatState {
        val card = state.playerHand.find { it.instanceId == handCardId } ?: return state
        if (!state.canPlayCard(card)) return state

        val cost = maxOf(0, state.effectivePlayCost(card))
        val isSpellLike = card.template.type == CardType.SPELL || card.template.type == CardType.CURSE
        val wasFirstCardThisRound = state.playerCardsPlayedThisRound == 0
        val wasNightfall = state.playerCardsPlayedThisRound > 0
        val payWithHealth = PowerResolver.shouldPayFirstCardWithHealth(state)

        val maxRegularMana = maxOf(state.playerMaxMana, state.playerMana).coerceIn(1, 10)
        var newMana = state.playerMana.coerceIn(0, maxRegularMana)
        var newSpellMana = state.playerSpellMana.coerceIn(0, 3)
        var nextPlayerNexusHp = state.playerNexusHp

        if (payWithHealth) {
            nextPlayerNexusHp = maxOf(0, nextPlayerNexusHp - cost)
        } else if (isSpellLike) {
            val spellUsed = minOf(newSpellMana, cost)
            newSpellMana = (newSpellMana - spellUsed).coerceIn(0, 3)
            newMana = (newMana - (cost - spellUsed)).coerceIn(0, maxRegularMana)
        } else {
            newMana = (newMana - cost).coerceIn(0, maxRegularMana)
        }

        val logs = state.combatLog.toMutableList()
        if (payWithHealth && cost > 0) {
            logs.add("Lời Nguyền Toàn Tri: Trả $cost Máu Nhà Chính thay Năng Lượng và sao chép ${card.template.name}!")
        }
        val itemManaRefill = card.attachedItems.sumOf { it.refillManaOnPlay }
        if (itemManaRefill > 0) {
            val refillCap = state.playerMaxMana.coerceIn(1, 10)
            newMana = minOf(refillCap, newMana + itemManaRefill).coerceIn(0, refillCap)
            logs.add("${card.template.name} hồi +$itemManaRefill Năng Lượng.")
        }

        val isInCombat = state.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        val usedFastInCombat = isSpellLike &&
            card.template.spellSpeed == SpellSpeed.FAST &&
            isInCombat

        var s = state.copy(
            playerNexusHp = nextPlayerNexusHp,
            playerMana = newMana,
            playerSpellMana = newSpellMana,
            playerHand = state.playerHand.filterNot { it.instanceId == handCardId },
            playerCardsPlayedThisRound = state.playerCardsPlayedThisRound + 1,
            playerFastSpellUsedInCombat = state.playerFastSpellUsedInCombat || usedFastInCombat,
            combatLog = logs
        )

        s = PowerResolver.onCreatedCardPlayed(s, card, logs)

        // Augment: +1|+0 to existing allied Augment units when playing a created card (before resolution)
        if (card.isCreated) {
            var augmentTriggered = false
            s = s.copy(playerBoard = s.playerBoard.map { u ->
                if (!u.isSilenced && u.keywords.contains(Keyword.AUGMENT)) {
                    augmentTriggered = true
                    u.copy(power = u.power + 1)
                } else u
            })
            if (augmentTriggered) {
                logs.add("Nâng Cấp kích hoạt (+1|+0) từ lá bài được tạo ${card.template.name}!")
            }
        }

        // Jinx 1★ / 3★ Power: Playing a card deals burn damage
        if (s.championId == "champ_jinx" && s.starLevel >= 1) {
            s = dealDamageToEnemyNexus(s, 1, logs)
            if (s.starLevel >= 3 && s.enemyBoard.isNotEmpty()) {
                val target = s.enemyBoard.random()
                s = damageUnit(s, target.instanceId, isEnemy = true, damage = 1, logs = logs)
            }
            logs.add("Sức Mạnh Sao Jinx gây 1 sát thương!")
        }

        // Play: Draw 1 item or card hook
        if (card.attachedItems.any { it.onPlayDrawOne } ||
            card.template.id == "card_zaunite_urchin" ||
            card.template.id == "card_shadow_assassin"
        ) {
            s = drawPlayerCard(s)
        }

        // Mutator: Glorious Assembly
        if (s.mutators.any { it.id == "mut_glorious_assembly" } && isSpellLike) {
            s = s.copy(playerBoard = s.playerBoard.map { it.copy(power = it.power + 1) })
        }

        var nextPriority = PriorityHolder.PLAYER
        var nextPhase = s.phase
        var nextLastCaster = s.lastStackCaster

        when (card.template.type) {
            CardType.UNIT, CardType.CHAMPION, CardType.LANDMARK -> {
                if (card.template.requiresAllySacrificeOnPlay) {
                    val sacrificeTarget = targetUnitId?.let { id ->
                        s.playerBoard.find { it.instanceId == id && it.template.type != CardType.LANDMARK }
                    } ?: s.playerBoard
                        .filter { it.template.type != CardType.LANDMARK }
                        .minByOrNull { it.power + it.health }
                    if (sacrificeTarget != null) {
                        s = removeUnit(s, sacrificeTarget.instanceId, isEnemy = false, logs = logs)
                        logs.add("Hy sinh ${sacrificeTarget.template.name} để triệu hồi ${card.template.name}!")
                    }
                }
                val (summonedState, playedUnitId) = summonPlayerUnit(s, card, logs, targetUnitId = targetUnitId)
                s = summonedState

                s = PowerResolver.onAfterUnitSummonedFromHand(s, card, playedUnitId, wasNightfall, logs)

                // Relic: Cruel Experiments (deal 1 and grant Overwhelm & Impact to played allies with 2+ Health)
                if (s.equippedRelics.any { it.id == "R0122" } && card.template.type != CardType.LANDMARK) {
                    val lastUnit = s.playerBoard.lastOrNull()
                    if (lastUnit != null && lastUnit.health >= 2) {
                        s = s.copy(playerBoard = s.playerBoard.map { u ->
                            if (u.instanceId == lastUnit.instanceId) {
                                u.copy(
                                    keywords = u.keywords + Keyword.OVERWHELM + Keyword.IMPACT,
                                    impactStacks = u.impactStacks + 1
                                )
                            } else u
                        })
                        s = damageUnit(s, lastUnit.instanceId, isEnemy = false, damage = 1, isSpell = false, fromAllyEffect = true, logs = logs)
                    }
                }
                if (card.template.isChampion) {
                    // Lost Chapter / Hidden Tome: refill spell mana on Champion play
                    if (s.equippedRelics.any { it.refillSpellManaOnPlay }) {
                        s = s.copy(playerSpellMana = 3)
                        logs.add("Hồi đầy Năng Lượng Phép!")
                    }
                    // Great Talons: 6+ base cost unit deals 2 to all enemies and enemy Nexus
                    if (s.equippedRelics.any { it.id == "R0126" } && card.template.cost >= 6) {
                        s = dealDamageToEnemyNexus(s, 2, logs)
                        s.enemyBoard.toList().forEach { e ->
                            s = damageUnit(s, e.instanceId, isEnemy = true, damage = 2, isSpell = false, logs = logs)
                        }
                        logs.add("Great Talons gây 2 sát thương lên toàn bộ địch!")
                    }
                    // Frost Breath: Play Frostbite strongest enemy (or ALL if Anivia)
                    if (s.equippedRelics.any { it.id == "R0143" }) {
                        if (card.template.id == "card_anivia") {
                            s.enemyBoard.toList().forEach { e ->
                                s = frostbiteUnit(s, e.instanceId, isEnemy = true, isSpell = false, fromAllyEffect = false, logs = logs)
                            }
                            logs.add("Frost Breath Đóng Băng TẤT CẢ kẻ địch!")
                        } else {
                            s.enemyBoard.maxByOrNull { it.effectivePower }?.let { strong ->
                                s = frostbiteUnit(s, strong.instanceId, isEnemy = true, isSpell = false, fromAllyEffect = false, logs = logs)
                                logs.add("Frost Breath Đóng Băng ${strong.template.name}!")
                            }
                        }
                    }
                    // Wicked Harvest: Play deal 3 to all other units
                    if (s.equippedRelics.any { it.id == "R0090" }) {
                        val justSummonedId = s.playerBoard.lastOrNull { it.template.id == card.template.id }?.instanceId
                        s.enemyBoard.toList().forEach { e ->
                            s = damageUnit(s, e.instanceId, isEnemy = true, damage = 3, isSpell = false, logs = logs)
                        }
                        s.playerBoard.toList().forEach { a ->
                            if (a.instanceId != justSummonedId) {
                                s = damageUnit(s, a.instanceId, isEnemy = false, damage = 3, isSpell = false, fromAllyEffect = true, logs = logs)
                            }
                        }
                        logs.add("Wicked Harvest gây 3 sát thương lên mọi đơn vị khác!")
                    }
                    // Disciple of Shadows: Play deal 3 to all other allies
                    if (s.equippedRelics.any { it.id == "R0060" }) {
                        val justSummonedId = s.playerBoard.lastOrNull { it.template.id == card.template.id }?.instanceId
                        s.playerBoard.toList().forEach { a ->
                            if (a.instanceId != justSummonedId) {
                                s = damageUnit(s, a.instanceId, isEnemy = false, damage = 3, isSpell = false, fromAllyEffect = true, logs = logs)
                            }
                        }
                    }
                    // Utmost Despair: Play grant enemies Gloom (-1|-1)
                    if (s.equippedRelics.any { it.id == "R0095" }) {
                        s = s.copy(enemyBoard = s.enemyBoard.map { e ->
                            e.copy(gloomStacks = e.gloomStacks + 1, power = maxOf(0, e.power - 1), health = e.health - 1, maxHealth = maxOf(1, e.maxHealth - 1))
                        }.filter { it.health > 0 })
                        logs.add("Utmost Despair yểm U Ám lên quân địch!")
                    }
                    // The Curator's Gatebreaker: Play strike the enemy Nexus
                    if (s.equippedRelics.any { it.id == "R0038" }) {
                        val champOnBoard = s.playerBoard.lastOrNull { it.template.id == card.template.id }
                        if (champOnBoard != null && champOnBoard.strikePower > 0) {
                            s = dealDamageToEnemyNexus(s, champOnBoard.strikePower, logs)
                            s = onUnitStrike(s, champOnBoard.instanceId, isPlayerStriker = true, hitNexus = true, logs = logs)
                            logs.add("The Curator's Gatebreaker: ${champOnBoard.template.name} đánh Nhà Chính địch gây ${champOnBoard.strikePower} sát thương!")
                        }
                    }
                }
                if (card.template.effectType == CardEffectType.FROSTBITE_TARGET) {
                    val chosen = targetUnitId?.let { id -> s.enemyBoard.find { it.instanceId == id } }
                        ?: s.enemyBoard.maxByOrNull { it.effectivePower }
                    if (chosen != null) {
                        s = frostbiteUnit(s, chosen.instanceId, isEnemy = true, isSpell = false, fromAllyEffect = false, logs = logs)
                    }
                }
                if (card.template.id == "card_brightsteel_protector") {
                    val top = targetUnitId?.let { id -> s.playerBoard.find { it.instanceId == id } }
                        ?: s.playerBoard.maxByOrNull { it.effectivePower }
                    top?.let { ally ->
                        s = triggerFatedOnTarget(s, ally.instanceId, sourceIsPlayer = true, logs = logs)
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == ally.instanceId) it.copy(hasBarrier = true) else it
                        })
                        s = refreshEvolveAndSeenKeywords(s, logs)
                    }
                }
                val (postPowerState, triggeredTimelineTransform) = PowerResolver.onPostUnitPlayPowerEffects(
                    state = s,
                    card = card,
                    playedUnitId = playedUnitId,
                    payWithHealth = payWithHealth,
                    logs = logs
                )
                s = postPowerState
                nextPriority = if (triggeredTimelineTransform) {
                    PriorityHolder.PLAYER
                } else {
                    // Rule 9.3: Summoning Unit/Landmark passes priority immediately to opponent
                    PriorityHolder.ENEMY
                }
            }
            CardType.EQUIPMENT -> {
                val host = targetUnitId?.let { id -> s.playerBoard.find { it.instanceId == id } }
                    ?: s.playerBoard.maxByOrNull { it.effectivePower }
                if (host != null) {
                    val (_, previousGear) = detachEquipmentFromUnit(host)
                    if (previousGear != null) {
                        s = returnEquipmentToHand(s, previousGear, toPlayer = true, logs = logs)
                    }
                    s = triggerFatedOnTarget(s, host.instanceId, sourceIsPlayer = true, logs = logs)
                    val extraGearBuff = PowerResolver.computeEquipmentP0914Bonus(
                        state = s,
                        host = host,
                        hadPreviousGear = previousGear != null
                    )
                    s = s.copy(playerBoard = s.playerBoard.map { unit ->
                        if (unit.instanceId == host.instanceId) {
                            val (baseUnit, _) = detachEquipmentFromUnit(unit)
                            baseUnit.copy(
                                power = baseUnit.power + card.template.basePower + extraGearBuff,
                                health = baseUnit.health + card.template.baseHealth + extraGearBuff,
                                maxHealth = baseUnit.maxHealth + card.template.baseHealth + extraGearBuff,
                                keywords = baseUnit.keywords + card.template.keywords,
                                hasBarrier = baseUnit.hasBarrier || card.template.keywords.contains(Keyword.BARRIER),
                                hasSpellShield = baseUnit.hasSpellShield || card.template.keywords.contains(Keyword.SPELLSHIELD),
                                hasDeathless = baseUnit.hasDeathless || card.template.keywords.contains(Keyword.DEATHLESS),
                                equippedGear = card.template,
                                equippedGearName = card.template.name
                            )
                        } else unit
                    })
                    s = refreshEvolveAndSeenKeywords(s, logs)
                    logs.add("Trang bị ${card.template.name} cho ${host.template.name}!")
                    s = PowerResolver.onEquipmentPlayed(s, card, host.instanceId, logs)
                }
                // Rule 9.3: Equipment passes priority immediately to opponent
                nextPriority = PriorityHolder.ENEMY
            }
            CardType.SPELL, CardType.CURSE -> {
                if (card.template.effectType == CardEffectType.PURGE_SHACKLES_CURSE) {
                    val shackledTargetId = card.linkedShackledUnitId
                        ?: s.playerBoard.firstOrNull { it.isShackled }?.instanceId
                    if (shackledTargetId != null) {
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == shackledTargetId) it.copy(isShackled = false, keywords = it.keywords - Keyword.SHACKLED) else it
                        })
                    }
                }
                s = s.copy(
                    playerSpellsManaSpent = s.playerSpellsManaSpent + cost,
                    playerSpellsPlayedThisRound = s.playerSpellsPlayedThisRound + 1
                )

                s = PowerResolver.onSpellPlayed(s, card, cost, logs)

                if (card.template.keywords.contains(Keyword.ELEMENTAL)) {
                    s = onPlayerSkillOrElementalPlayed(s, logs, card.template.name)
                }

                // Lux Star Power 1★: Spell grants strongest ally +1|+1
                if (s.championId == "champ_lux" && s.starLevel >= 1) {
                    s.playerBoard.maxByOrNull { it.effectivePower }?.let { top ->
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == top.instanceId) {
                                it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                            } else it
                        })
                    }
                }
                // Lux 3★: Every 6 spell mana -> Rally + Final Spark
                if (s.championId == "champ_lux" && s.starLevel >= 3 && s.playerSpellsManaSpent >= 6) {
                    s = s.copy(
                        playerSpellsManaSpent = s.playerSpellsManaSpent - 6
                    )
                    s = grantPlayerAttackToken(s, logs, "Lux 3★")
                    if (s.playerHand.size < 10) {
                        s = s.copy(playerHand = s.playerHand + createHandInstance(CardCatalog.getCard("card_final_spark"), true))
                    }
                    logs.add("Lux 3★ kích hoạt: Tập Hợp & tạo Cầu Vồng Tối Thượng!")
                }

                // P0118: Chia Sẻ Chiến Lợi Phẩm - Sao chép phép đơn mục tiêu lên đồng minh yếu nhất khác
                val shareTargetId = PowerResolver.resolveSingleTargetSpellShareId(s, card, targetUnitId)

                val spellCastCount = if (payWithHealth) 2 else 1

                // Rule 9.3: Burst/Focus resolve immediately and KEEP priority; Fast/Slow enter stack and pass priority
                if (card.template.spellSpeed == SpellSpeed.BURST || card.template.spellSpeed == SpellSpeed.FOCUS) {
                    repeat(spellCastCount) {
                        s = resolveSingleSpellEffect(
                            state = s,
                            card = card.template,
                            sourceIsPlayer = true,
                            bonusDmg = 0,
                            logs = logs,
                            targetUnitId = targetUnitId
                        )
                        if (shareTargetId != null) {
                            s = resolveSingleSpellEffect(
                                state = s,
                                card = card.template,
                                sourceIsPlayer = true,
                                bonusDmg = 0,
                                logs = logs,
                                targetUnitId = shareTargetId
                            )
                        }
                    }
                    if (s.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && nextPhase != CombatPhase.COMBAT_DECLARE_BLOCK_PHASE) {
                        nextPhase = s.phase
                        nextPriority = s.priorityHolder
                    } else {
                        nextPriority = PriorityHolder.PLAYER
                    }
                } else {
                    // Fated triggers on play when an allied unit on board is targeted by Fast/Slow spell
                    val fatedTargetId = when (card.template.effectType) {
                        CardEffectType.SINGLE_COMBAT_STRIKE -> targetUnitId?.split("|")?.firstOrNull()
                        else -> targetUnitId
                    }
                    if (fatedTargetId != null) {
                        s = triggerFatedOnTarget(s, fatedTargetId, sourceIsPlayer = true, logs = logs)
                    }
                    val entries = mutableListOf<SpellStackEntry>()
                    repeat(spellCastCount) {
                        entries.add(
                            SpellStackEntry(
                                id = "st_${nextId()}",
                                sourceIsPlayer = true,
                                card = card.template,
                                targetUnitId = targetUnitId,
                                damageBonus = 0
                            )
                        )
                        if (shareTargetId != null) {
                            entries.add(
                                SpellStackEntry(
                                    id = "st_${nextId()}",
                                    sourceIsPlayer = true,
                                    card = card.template,
                                    targetUnitId = shareTargetId,
                                    damageBonus = 0
                                )
                            )
                        }
                    }
                    s = s.copy(spellStack = s.spellStack + entries)
                    nextLastCaster = PriorityHolder.PLAYER
                    if (nextPhase == CombatPhase.MAIN_ACTION_PHASE) {
                        nextPhase = CombatPhase.SPELL_STACK_PHASE
                    }
                    nextPriority = PriorityHolder.ENEMY
                    logs.add("Đặt ${card.template.name} (${card.template.spellSpeed.displayName}) lên hàng chờ phép.")
                }
            }
        }

        val castDuringBlock = isSpellLike && s.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        s = checkChampionLevelUps(s, logs)
        s = syncChampionSpellsInHand(s)
        s = s.copy(
            phase = nextPhase,
            priorityHolder = nextPriority,
            consecutivePasses = 0,
            lastStackCaster = nextLastCaster,
            playerCastSpellInBlock = s.playerCastSpellInBlock || castDuringBlock,
            combatLog = logs.takeLast(16)
        )
        return checkGameOver(s)
    }

    internal fun summonPlayerUnit(
        state: CombatState,
        card: HandCardInstance,
        logs: MutableList<String>,
        forceEphemeral: Boolean = false,
        targetUnitId: String? = null
    ): Pair<CombatState, String?> {
        if (state.playerBoard.size >= 6) return state to null

        var s = state
        val tmpl = card.template
        val itemKeywords = card.extraKeywords
        var keywords = tmpl.keywords + itemKeywords
        if (forceEphemeral) keywords = keywords + Keyword.EPHEMERAL

        // Allies aura keywords from Relics (Troll King's Crown, Laurent Bladerack, Pack-A-Punch, The Beast Within)
        s.equippedRelics.forEach { relic ->
            if (relic.alliesGrantedKeywords.isNotEmpty()) {
                keywords = keywords + relic.alliesGrantedKeywords
            }
        }
        // Black Shield: allies gain SpellShield on summon
        if (s.equippedRelics.any { it.id == "R0114" }) {
            keywords = keywords + Keyword.SPELLSHIELD
        }
        // Spirit of the Buhru: allies costing 1 or less have Overwhelm, Quick Attack, and Fury
        if (s.equippedRelics.any { it.id == "R0079" } && tmpl.cost <= 1) {
            keywords = keywords + Keyword.OVERWHELM + Keyword.QUICK_ATTACK + Keyword.FURY
        }
        // Strength of Stone: allies gain +0|+1 and Formidable on summon
        val strengthOfStoneCount = s.equippedRelics.count { it.id == "R0091" }
        if (strengthOfStoneCount > 0) {
            keywords = keywords + Keyword.FORMIDABLE
        }

        // Hextech Fabricator random keyword
        if (card.attachedItems.any { it.grantRandomKeywordOnSummon }) {
            keywords = keywords + Keyword.sharablePool.random()
        }

        var power = tmpl.basePower + card.bonusPower
        var health = tmpl.baseHealth + card.bonusHealth + strengthOfStoneCount
        if (tmpl.id == "card_encroaching_mist") {
            power += s.playerMistStatBonus
            health += s.playerMistStatBonus
        } else if (tmpl.id == "card_viego") {
            power += s.playerViegoStatBonus
            health += s.playerViegoStatBonus
        }
        if (tmpl.isChampion) {
            power += s.equippedRelics.sumOf { it.bonusPower }
            health += s.equippedRelics.sumOf { it.bonusHealth }
            s.equippedRelics.flatMap { it.allGrantedKeywords }.forEach { keywords = keywords + it }
            // Star Gem: Allied champions have +1|+1
            val starGemCount = s.equippedRelics.count { it.id == "R0043" }
            power += starGemCount
            health += starGemCount
        } else if (tmpl.isDragon && s.equippedRelics.any { it.id == "R0080" }) {
            // The Beast Within: subtype allies get +1|+1
            power += 1; health += 1
        }
        if (s.championId == "champ_garen" && s.starLevel >= 2) {
            power += 1; health += 1
        }
        if (s.championId == "champ_garen" && s.starLevel >= 4) {
            keywords = keywords + Keyword.TOUGH
        }
        if (s.championId == "champ_lux" && s.starLevel >= 4) {
            health += 2; keywords = keywords + Keyword.SHARPSIGHT
        }
        if (s.championId == "champ_jinx" && s.starLevel >= 4) {
            health += 1; keywords = keywords + Keyword.IMPACT
        }
        if (s.championId == "champ_yasuo" && s.starLevel >= 4) {
            power += 1; health += 1
        }
        if (s.championId == "champ_yasuo" && s.starLevel >= 2 && keywords.contains(Keyword.QUICK_ATTACK)) {
            power += 1
        }

        // Daybreak bonus
        if (keywords.contains(Keyword.DAYBREAK) && s.playerCardsPlayedThisRound == 1) {
            power += 1; health += 1
            keywords = keywords + Keyword.CHALLENGER
            logs.add("Hừng Đông kích hoạt trên ${tmpl.name} (+1|+1 & Thách Đấu)!")
        }

        // Spirit stack bonus
        val relicSpiritCount = if (tmpl.isChampion) s.equippedRelics.sumOf { it.spiritStacks } else 0
        val spiritCount = tmpl.spiritStacks + card.attachedItems.sumOf { it.spiritStacks } + relicSpiritCount
        power += spiritCount
        health += spiritCount

        val relicImpactCount = s.equippedRelics.sumOf { it.alliesImpactStacks } +
            (if (tmpl.isChampion) s.equippedRelics.sumOf { it.impactStacks } else 0)
        val impactCount = tmpl.impactStacks + card.attachedItems.sumOf { it.impactStacks } + relicImpactCount +
            (if (s.championId == "champ_jinx" && s.starLevel >= 4) 1 else 0)

        // Lurk bonus accumulated this match
        if (keywords.contains(Keyword.LURK)) {
            power += s.playerLurkBonus
        }

        // Deep bonus (+3|+3 when deck has <= 15 cards)
        var deepApplied = false
        if (keywords.contains(Keyword.DEEP) && s.playerDrawPile.size <= 15) {
            power += 3
            health += 3
            deepApplied = true
            logs.add("Thẩm Sâu kích hoạt trên ${tmpl.name} (+3|+3)!")
        }

        // Track Evolve unique positive keywords
        val newSeenKeywords = s.playerUniqueKeywordsSeen + keywords.filter { it.isSharablePositive }
        var evolveApplied = false
        if (keywords.contains(Keyword.EVOLVE) && newSeenKeywords.size >= 6) {
            power += 2; health += 2
            evolveApplied = true
        }

        val isAllyUnit = tmpl.type != CardType.LANDMARK
        val summonMod = PowerResolver.applySummonStatModifiers(
            state = s,
            card = card,
            isAllyUnit = isAllyUnit,
            initialPower = power,
            initialHealth = health,
            initialKeywords = keywords
        )
        power = summonMod.power
        health = summonMod.health
        keywords = summonMod.keywords
        val grandPlazaBonusPow = summonMod.grandPlazaBonusPow
        val grandPlazaBonusHp = summonMod.grandPlazaBonusHp
        val grandPlazaAddedKws = summonMod.grandPlazaAddedKeywords

        val unitId = "u_${nextId()}"
        val rawUnit = UnitOnBoard(
            instanceId = unitId,
            template = tmpl,
            ownerIsPlayer = true,
            power = power,
            health = health,
            maxHealth = health,
            keywords = keywords,
            impactStacks = impactCount,
            spiritStacks = spiritCount,
            hasBarrier = keywords.contains(Keyword.BARRIER),
            hasSpellShield = keywords.contains(Keyword.SPELLSHIELD),
            hasDeathless = keywords.contains(Keyword.DEATHLESS),
            attachedItems = card.attachedItems,
            countdownRemaining = tmpl.countdownRounds,
            hasEnlightenedBonusApplied = false,
            hasDeepBonusApplied = deepApplied,
            hasEvolveBonusApplied = evolveApplied
        )
        val unit = applyPluckyPoroEmpowerment(PowerResolver.applyPlayerBoardAuras(rawUnit, s))

        // Attune
        if (unit.keywords.contains(Keyword.ATTUNE)) {
            s = s.copy(playerSpellMana = minOf(3, s.playerSpellMana + 1))
        }

        val updatedBoard = PowerResolver.shareKeywordsOnSummon(s, unit)

        val updatedTempPow = if (grandPlazaBonusPow > 0) {
            s.roundTempPowerBuffs + (unitId to ((s.roundTempPowerBuffs[unitId] ?: 0) + grandPlazaBonusPow))
        } else s.roundTempPowerBuffs
        val updatedTempHp = if (grandPlazaBonusHp > 0) {
            s.roundTempHealthBuffs + (unitId to ((s.roundTempHealthBuffs[unitId] ?: 0) + grandPlazaBonusHp))
        } else s.roundTempHealthBuffs
        val updatedTempKws = if (grandPlazaAddedKws.isNotEmpty()) {
            s.roundTempKeywords + (unitId to ((s.roundTempKeywords[unitId] ?: emptySet()) + grandPlazaAddedKws))
        } else s.roundTempKeywords

        s = s.copy(
            playerBoard = updatedBoard + unit,
            playerUniqueKeywordsSeen = newSeenKeywords,
            roundTempPowerBuffs = updatedTempPow,
            roundTempHealthBuffs = updatedTempHp,
            roundTempKeywords = updatedTempKws
        )

        s = PowerResolver.onPostAllySummonEffects(s, unitId, unit, isAllyUnit, logs)
        s = refreshEvolveAndSeenKeywords(s, logs)

        if (isAllyUnit && s.championId == "champ_tryndamere" && s.starLevel >= 6) {
            s.playerBoard.find { it.instanceId == unitId }?.let { liveUnit ->
                val addPow = maxOf(0, liveUnit.power)
                val addHp = maxOf(0, liveUnit.health)
                s = s.copy(
                    playerBoard = s.playerBoard.map { u ->
                        if (u.instanceId == unitId) {
                            applyPluckyPoroEmpowerment(
                                u.copy(
                                    power = u.power + addPow,
                                    health = u.health + addHp,
                                    maxHealth = u.maxHealth + addHp
                                )
                            )
                        } else u
                    },
                    roundTempPowerBuffs = s.roundTempPowerBuffs + (unitId to ((s.roundTempPowerBuffs[unitId] ?: 0) + addPow)),
                    roundTempHealthBuffs = s.roundTempHealthBuffs + (unitId to ((s.roundTempHealthBuffs[unitId] ?: 0) + addHp))
                )
                logs.add("Hiệp Ước Huyết Thống (Tryndamere 6★): Gấp đôi chỉ số ${liveUnit.template.name} trong vòng này!")
            }
        }
        val finalUnit = s.playerBoard.find { it.instanceId == unitId } ?: unit
        logs.add("Triệu hồi ${tmpl.name} (${finalUnit.power}|${finalUnit.health}).")

        // On-Summon Skills & Triggers (Pipelines 3.3 & 4.2)
        when (tmpl.id) {
            "card_wild_mystics" -> {
                if (s.playerHand.size < 10) {
                    val arrow = createHandInstance(CardCatalog.getCard("card_frozen_arrow"), isCreated = true)
                    s = s.copy(playerHand = s.playerHand + arrow)
                    logs.add("Thầy Tu Hoang Dã tạo 1 lá Tên Đóng Băng lên tay!")
                }
            }
            "card_flame_eyed_caprine" -> {
                val capDmg = calculateFinalDamage(1, tmpl, s, sourceIsPlayer = true)
                s = dealDamageToEnemyNexus(s, capDmg, logs)
                s.enemyBoard.toList().forEach { enemy ->
                    s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = capDmg, isSpell = true, fromAllyEffect = false, logs = logs)
                }
                logs.add("Hỏa Nhân gây $capDmg sát thương lên Nhà Chính địch và toàn bộ quân địch!")
            }
            "card_crystal_stormbird" -> {
                s = onPlayerSkillOrElementalPlayed(s, logs, tmpl.name)
                val stormDmg = calculateFinalDamage(2, tmpl, s, sourceIsPlayer = true)
                s.enemyBoard.toList().forEach { enemy ->
                    s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = stormDmg, isSpell = true, fromAllyEffect = false, logs = logs)
                }
                s.playerBoard.toList().forEach { ally ->
                    if (ally.instanceId != unit.instanceId) {
                        s = damageUnit(s, ally.instanceId, isEnemy = false, damage = stormDmg, isSpell = true, fromAllyEffect = true, logs = logs)
                    }
                }
                logs.add("Kỹ Năng Chim Bão gây $stormDmg sát thương lên TẤT CẢ bài quân khác!")
            }
            "card_rhasa" -> {
                val victims = s.enemyBoard
                    .filter { it.template.type != CardType.LANDMARK }
                    .sortedWith(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health })
                    .take(2)
                victims.forEach { v ->
                    s = removeUnit(s, v.instanceId, isEnemy = true, logs = logs)
                }
                if (victims.isNotEmpty()) {
                    logs.add("Rhasa Phân Ly tiêu diệt ${victims.size} kẻ địch có Công thấp nhất!")
                }
            }
            "card_rumul" -> {
                val targetAlly = s.playerBoard
                    .filter { it.template.type != CardType.LANDMARK }
                    .maxByOrNull { it.health }
                if (targetAlly != null) {
                    s = s.copy(playerBoard = s.playerBoard.map { u ->
                        if (u.instanceId == targetAlly.instanceId) {
                            u.copy(health = u.health + 4, maxHealth = u.maxHealth + 4)
                        } else u
                    })
                    logs.add("Rumul ban +0|+4 cho ${targetAlly.template.name}!")
                }
            }
            "card_galio" -> {
                s = s.copy(playerBoard = s.playerBoard.map { u ->
                    if (u.template.type != CardType.LANDMARK) {
                        u.copy(health = u.health + 3, maxHealth = u.maxHealth + 3)
                    } else u
                })
                logs.add("Galio ban +0|+3 cho toàn bộ đồng minh!")
            }
            "card_windfarer_hatchling" -> {
                val nextPow = s.roundTempPowerBuffs.toMutableMap()
                val nextHp = s.roundTempHealthBuffs.toMutableMap()
                s = s.copy(playerBoard = s.playerBoard.map { u ->
                    if (u.instanceId != unitId && u.template.type != CardType.LANDMARK) {
                        nextPow[u.instanceId] = (nextPow[u.instanceId] ?: 0) + 2
                        nextHp[u.instanceId] = (nextHp[u.instanceId] ?: 0) + 2
                        u.copy(power = u.power + 2, health = u.health + 2, maxHealth = u.maxHealth + 2)
                    } else u
                }, roundTempPowerBuffs = nextPow, roundTempHealthBuffs = nextHp)
                logs.add("Rồng Gió Non ban +2|+2 cho đồng minh trong vòng này!")
            }
            "card_laurent_bladekeeper" -> {
                val targetAlly = targetUnitId?.let { id ->
                    s.playerBoard.find { it.instanceId == id && it.instanceId != unit.instanceId }
                } ?: s.playerBoard
                    .filter { it.instanceId != unit.instanceId && it.template.type != CardType.LANDMARK }
                    .maxByOrNull { it.effectivePower }
                if (targetAlly != null) {
                    s = s.copy(playerBoard = s.playerBoard.map { u ->
                        if (u.instanceId == targetAlly.instanceId) {
                            u.copy(power = u.power + 2, health = u.health + 2, maxHealth = u.maxHealth + 2)
                        } else u
                    })
                    logs.add("Người Giữ Gươm Laurent ban +2|+2 cho ${targetAlly.template.name}!")
                }
            }
            "card_tiana_crownguard" -> {
                s = s.copy(playerBoard = s.playerBoard.map { u ->
                    u.copy(keywords = u.keywords + Keyword.TOUGH)
                })
                logs.add("Tiana ban Kiên Cường cho toàn bộ đồng minh!")
            }
            "card_fading_icon" -> {
                if (s.playerBoard.size < 6) {
                    s = summonPlayerUnit(s, createHandInstance(CardCatalog.getCard("card_spiderling")), logs).first
                }
            }
            "card_camavoran_soldier", "card_invasive_hydravine" -> {
                if (s.playerBoard.size < 6) {
                    s = summonPlayerUnit(s, createHandInstance(CardCatalog.getCard("card_encroaching_mist")), logs).first
                }
            }
            "card_encroaching_mist" -> {
                s = applyEncroachingMistSummonBuff(s, forPlayer = true, logs = logs)
            }
            "card_avarosan_hearthguard" -> {
                s = grantStatsToDeckUnits(s, count = null, powerGain = 1, healthGain = 1, logs = logs, sourceLabel = tmpl.name)
            }
            "card_glacial_saurian" -> {
                s = grantStatsToDeckUnits(s, count = 3, powerGain = 1, healthGain = 1, logs = logs, sourceLabel = tmpl.name)
                val mySubtype = tmpl.resolvedSubtype
                val (afterDraw, drawnCard) = drawSpecificUnitFromDeck(s, logs) { candidate ->
                    val sub = candidate.template.resolvedSubtype
                    (candidate.template.type == CardType.UNIT || candidate.template.type == CardType.CHAMPION) &&
                        sub != null && sub != mySubtype
                }
                s = afterDraw
                if (drawnCard != null) {
                    logs.add("${tmpl.name}: Rút 1 bài quân khác nhóm bài phụ (${drawnCard.template.name})!")
                }
            }
        }

        // Đại Bàng Trinh Sát đang ở trên sân nhận Thách Đấu khi đồng minh khác được triệu hồi
        s = grantFleetfeatherChallengerOnSummon(s, newlySummonedId = unit.instanceId, forPlayer = true)

        // Relic: Stalker's Blade / Scales of Judgment
        if (tmpl.isChampion && s.equippedRelics.any { it.strikeWeakestOnSummon }) {
            s.enemyBoard.minByOrNull { it.health }?.let { weakest ->
                s = performNonCombatStrike(
                    state = s,
                    strikerId = unit.instanceId,
                    strikerIsPlayer = true,
                    targetId = weakest.instanceId,
                    targetIsEnemy = true,
                    logs = logs
                )
                logs.add("Cổ Vật Ra Đòn: ${tmpl.name} đánh ${weakest.template.name}!")
            }
        }
        // Relic: Ravenous Hydra (deal 1 to all enemies)
        if (tmpl.isChampion && s.equippedRelics.any { it.id == "R0016" }) {
            s.enemyBoard.toList().forEach { e ->
                s = damageUnit(s, e.instanceId, isEnemy = true, damage = 1, isSpell = false, logs = logs)
            }
            logs.add("Ravenous Hydra gây 1 sát thương lên toàn bộ quân địch!")
        }
        // Relic: Everfrost (Stun strongest enemy)
        if (tmpl.isChampion && s.equippedRelics.any { it.id == "R0018" }) {
            s.enemyBoard.maxByOrNull { it.effectivePower }?.let { strong ->
                s = stunEnemyUnit(s, strong.instanceId, logs)
            }
        }
        // Relic: True Ice Flail (Give enemies Vulnerable)
        if (tmpl.isChampion && s.equippedRelics.any { it.id == "R0039" }) {
            s = s.copy(enemyBoard = s.enemyBoard.map { it.copy(keywords = it.keywords + Keyword.VULNERABLE) })
            logs.add("True Ice Flail ban Dễ Tổn Thương cho quân địch!")
        }
        // Relic: Whirling Blade (Summon: deal 1 to all enemies and enemy Nexus)
        if (tmpl.isChampion && s.equippedRelics.any { it.id == "R0160" }) {
            s = dealDamageToEnemyNexus(s, 1, logs)
            s.enemyBoard.toList().forEach { e ->
                s = damageUnit(s, e.instanceId, isEnemy = true, damage = 1, isSpell = false, logs = logs)
            }
            logs.add("Whirling Blade gây 1 sát thương lên toàn bộ địch!")
        }
        // Relic: Mark of the Kindred (shrink weakest enemy health to 1)
        if (tmpl.isChampion && s.equippedRelics.any { it.id == "R0185" }) {
            s.enemyBoard.minByOrNull { it.health }?.let { weakest ->
                s = s.copy(enemyBoard = s.enemyBoard.map {
                    if (it.instanceId == weakest.instanceId) it.copy(health = 1, maxHealth = 1) else it
                })
                logs.add("Mark of the Kindred giảm máu ${weakest.template.name} xuống 1!")
            }
        }

        // Shadow Totem Ephemeral Copy
        if (!forceEphemeral && card.attachedItems.any { it.onSummonEphemeralCopy }) {
            if (s.playerBoard.size < 6) {
                s = summonPlayerUnit(s, card, logs, forceEphemeral = true).first
            }
        }

        return syncChampionSpellsInHand(s) to unitId
    }

    fun resolveSpellStack(state: CombatState): CombatState {
        if (state.spellStack.isEmpty()) return state
        var s = state
        val logs = s.combatLog.toMutableList()
        // LIFO stack resolution (top of stack resolves first)
        for (entry in state.spellStack.reversed()) {
            s = resolveSingleSpellEffect(
                state = s,
                card = entry.card,
                sourceIsPlayer = entry.sourceIsPlayer,
                bonusDmg = entry.damageBonus,
                logs = logs,
                targetUnitId = entry.targetUnitId
            )
        }
        val returnPhase = if (s.phase == CombatPhase.SPELL_STACK_PHASE) {
            CombatPhase.MAIN_ACTION_PHASE
        } else {
            s.phase
        }
        val nextPriority = when (returnPhase) {
            CombatPhase.COMBAT_DECLARE_ATTACK_PHASE -> PriorityHolder.PLAYER
            CombatPhase.COMBAT_DECLARE_BLOCK_PHASE ->
                if (s.playerInitiatedCombat) PriorityHolder.ENEMY else PriorityHolder.PLAYER
            else -> s.lastStackCaster?.opponent() ?: s.priorityHolder
        }
        s = s.copy(
            spellStack = emptyList(),
            phase = returnPhase,
            priorityHolder = nextPriority,
            consecutivePasses = 0,
            lastStackCaster = null,
            combatLog = logs.takeLast(16)
        )
        s = checkChampionLevelUps(s, logs)
        s = checkGameOver(s)
        if (s.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && s.blockCommitted && s.playerWon == null) {
            return if (s.playerInitiatedCombat) {
                finishPlayerAttackCombat(s)
            } else {
                finishEnemyAttackCombat(s)
            }
        }
        return s
    }

    internal fun resolveSingleSpellEffect(
        state: CombatState,
        card: CardTemplate,
        sourceIsPlayer: Boolean,
        bonusDmg: Int = 0,
        logs: MutableList<String>,
        targetUnitId: String? = null
    ): CombatState {
        var s = state
        val dmg = calculateFinalDamage(card.effectValue, card, s, sourceIsPlayer, bonusDmg)

        when (card.effectType) {
            CardEffectType.DEAL_DAMAGE_ANY -> {
                if (targetUnitId == "ENEMY_NEXUS" || (sourceIsPlayer && targetUnitId == "NEXUS")) {
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    logs.add("${card.name} gây $dmg sát thương lên Nhà Chính địch.")
                } else if (targetUnitId == "PLAYER_NEXUS" || (!sourceIsPlayer && targetUnitId == "NEXUS")) {
                    s = dealDamageToPlayerNexus(s, dmg, logs)
                    logs.add("${card.name} của địch gây $dmg sát thương lên Nhà Chính của bạn.")
                } else if (targetUnitId != null) {
                    val onEnemy = s.enemyBoard.any { it.instanceId == targetUnitId }
                    val onPlayer = s.playerBoard.any { it.instanceId == targetUnitId }
                    if (onEnemy) {
                        if (!sourceIsPlayer) {
                            s = triggerFatedOnTarget(s, targetUnitId, sourceIsPlayer = false, logs = logs)
                        }
                        s = damageUnit(s, targetUnitId, isEnemy = true, damage = dmg, isSpell = true, fromAllyEffect = !sourceIsPlayer, logs = logs)
                        logs.add("${card.name} gây $dmg sát thương.")
                    } else if (onPlayer) {
                        if (sourceIsPlayer) {
                            s = triggerFatedOnTarget(s, targetUnitId, sourceIsPlayer = true, logs = logs)
                        }
                        s = damageUnit(s, targetUnitId, isEnemy = false, damage = dmg, isSpell = true, fromAllyEffect = sourceIsPlayer, logs = logs)
                        logs.add("${card.name} gây $dmg sát thương.")
                    } else {
                        logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                    }
                } else if (sourceIsPlayer && isBurstDirectNexusDamageSpell(card)) {
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    logs.add("${card.name} gây $dmg sát thương lên Nhà Chính địch.")
                } else if (sourceIsPlayer) {
                    val target = s.enemyBoard.minByOrNull { it.health }
                    if (target != null) {
                        s = damageUnit(s, target.instanceId, isEnemy = true, damage = dmg, isSpell = true, fromAllyEffect = false, logs = logs)
                    } else {
                        s = dealDamageToEnemyNexus(s, dmg, logs)
                    }
                    logs.add("${card.name} gây $dmg sát thương.")
                } else {
                    val target = s.playerBoard.minByOrNull { it.health }
                    if (target != null) {
                        s = damageUnit(s, target.instanceId, isEnemy = false, damage = dmg, isSpell = true, fromAllyEffect = false, logs = logs)
                    } else {
                        s = dealDamageToPlayerNexus(s, dmg, logs)
                    }
                    logs.add("${card.name} của địch gây $dmg sát thương.")
                }
            }
            CardEffectType.DEAL_DAMAGE_NEXUS -> {
                if (sourceIsPlayer) {
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    logs.add("${card.name} gây $dmg sát thương lên Nhà Chính địch.")
                } else {
                    s = dealDamageToPlayerNexus(s, dmg, logs)
                    logs.add("${card.name} của địch gây $dmg sát thương lên Nhà Chính của bạn.")
                }
            }
            CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS -> {
                if (sourceIsPlayer) {
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    s.enemyBoard.toList().forEach { enemy ->
                        s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = maxOf(2, dmg / 2), isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                    logs.add("${card.name} bắn $dmg lên Nhà Chính & toàn bộ quân địch!")
                } else {
                    s = dealDamageToPlayerNexus(s, dmg, logs)
                    s.playerBoard.toList().forEach { ally ->
                        s = damageUnit(s, ally.instanceId, isEnemy = false, damage = maxOf(2, dmg / 2), isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                    logs.add("${card.name} của địch bắn $dmg lên Nhà Chính & quân của bạn!")
                }
            }
            CardEffectType.STUN_TARGET -> {
                val targetIsEnemy = sourceIsPlayer
                val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
                val target = if (targetUnitId != null) {
                    targetBoard.find { it.instanceId == targetUnitId }
                } else {
                    targetBoard.maxByOrNull { it.effectivePower }
                }
                val resolvedTarget = if (targetUnitId != null && target == null) {
                    logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                    false
                } else if (target != null) {
                    s = stunUnit(s, target.instanceId, isEnemy = targetIsEnemy, isSpell = true, fromAllyEffect = false, logs = logs)
                    true
                } else {
                    false
                }
                if (card.id == "card_concussive_palm" && (targetUnitId == null || resolvedTarget)) {
                    val tailTmpl = CardCatalog.getCard("card_tail_of_the_dragon")
                    if (sourceIsPlayer && s.playerBoard.size < 6) {
                        s = summonPlayerUnit(s, createHandInstance(tailTmpl), logs).first
                    } else if (!sourceIsPlayer && s.enemyBoard.size < 6) {
                        s = summonEnemyUnitFromPool(s, tailTmpl, spendMana = 0, logs = logs)
                    }
                }
            }
            CardEffectType.FROSTBITE_TARGET -> {
                val targetIsEnemy = sourceIsPlayer
                val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
                val target = if (targetUnitId != null) {
                    targetBoard.find { it.instanceId == targetUnitId }
                } else {
                    targetBoard.filter { !it.isFrostbitten }.maxByOrNull { it.effectivePower }
                        ?: targetBoard.maxByOrNull { it.effectivePower }
                }
                if (targetUnitId != null && target == null) {
                    logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                } else if (target != null) {
                    s = frostbiteUnit(s, target.instanceId, isEnemy = targetIsEnemy, isSpell = true, fromAllyEffect = false, logs = logs)
                }
            }
            CardEffectType.RECALL_OR_STUN -> {
                val targetIsEnemy = sourceIsPlayer
                val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
                val target = if (targetUnitId != null) {
                    targetBoard.find { it.instanceId == targetUnitId }
                } else {
                    targetBoard.maxByOrNull { it.effectivePower }
                }
                if (targetUnitId != null && target == null) {
                    logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                } else if (target != null) {
                    s = recallUnit(s, target.instanceId, isEnemy = targetIsEnemy, isSpell = true, fromAllyEffect = false, logs = logs)
                }
            }
            CardEffectType.GIVE_BARRIER_AND_DRAW -> {
                val allyBoard = if (sourceIsPlayer) s.playerBoard else s.enemyBoard
                val chosenAlly = if (targetUnitId != null) {
                    allyBoard.find { it.instanceId == targetUnitId }
                } else {
                    allyBoard.maxByOrNull { it.effectivePower }
                }
                if (chosenAlly != null) {
                    if (targetUnitId != null) {
                        s = triggerFatedOnTarget(s, chosenAlly.instanceId, sourceIsPlayer = sourceIsPlayer, logs = logs)
                    }
                    s = if (sourceIsPlayer) {
                        s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == chosenAlly.instanceId) it.copy(hasBarrier = true) else it
                        })
                    } else {
                        s.copy(enemyBoard = s.enemyBoard.map {
                            if (it.instanceId == chosenAlly.instanceId) it.copy(hasBarrier = true) else it
                        })
                    }
                    if (sourceIsPlayer) {
                        s = refreshEvolveAndSeenKeywords(s, logs)
                    }
                }
                s = if (sourceIsPlayer) {
                    logs.add("${card.name} ban Lá Chắn và rút 1 lá.")
                    drawPlayerCard(s)
                } else {
                    logs.add("${card.name} của địch ban Lá Chắn và rút 1 lá.")
                    drawEnemyCard(s)
                }
            }
            CardEffectType.RALLY_AND_SHIELD -> {
                val allyBoard = if (sourceIsPlayer) s.playerBoard else s.enemyBoard
                val top = if (targetUnitId != null) {
                    allyBoard.find { it.instanceId == targetUnitId }
                } else {
                    allyBoard.maxByOrNull { it.effectivePower }
                }
                if (sourceIsPlayer) {
                    top?.let { chosen ->
                        if (targetUnitId != null) {
                            s = triggerFatedOnTarget(s, chosen.instanceId, sourceIsPlayer = true, logs = logs)
                        }
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == chosen.instanceId) it.copy(hasBarrier = true) else it
                        })
                        s = refreshEvolveAndSeenKeywords(s, logs)
                    }
                    s = grantPlayerAttackToken(s, logs, card.name)
                    logs.add("Khiên Vàng Tập Hợp kích hoạt Tập Hợp!")
                } else {
                    top?.let { chosen ->
                        if (targetUnitId != null) {
                            s = triggerFatedOnTarget(s, chosen.instanceId, sourceIsPlayer = false, logs = logs)
                        }
                        s = s.copy(enemyBoard = s.enemyBoard.map {
                            if (it.instanceId == chosen.instanceId) it.copy(hasBarrier = true) else it
                        })
                    }
                    s = s.copy(enemyHasAttackToken = true)
                    logs.add("Khiên Vàng Tập Hợp của địch kích hoạt Tập Hợp!")
                }
            }
            CardEffectType.SINGLE_COMBAT_STRIKE -> {
                val parts = targetUnitId?.split("|")
                val ally = when {
                    parts != null && parts.size == 2 -> {
                        if (sourceIsPlayer) s.playerBoard.find { it.instanceId == parts[0] }
                        else s.enemyBoard.find { it.instanceId == parts[0] }
                    }
                    sourceIsPlayer -> s.playerBoard.maxByOrNull { it.strikePower }
                    else -> s.enemyBoard.maxByOrNull { it.strikePower }
                }
                val enemy = when {
                    parts != null && parts.size == 2 -> {
                        if (sourceIsPlayer) s.enemyBoard.find { it.instanceId == parts[1] }
                        else s.playerBoard.find { it.instanceId == parts[1] }
                    }
                    targetUnitId != null -> {
                        if (sourceIsPlayer) s.enemyBoard.find { it.instanceId == targetUnitId }
                        else s.playerBoard.find { it.instanceId == targetUnitId }
                    }
                    sourceIsPlayer -> s.enemyBoard.minByOrNull { it.health }
                    else -> s.playerBoard.minByOrNull { it.health }
                }
                if (ally != null && enemy != null) {
                    if (enemy.hasSpellShield) {
                        s = if (sourceIsPlayer) {
                            s.copy(enemyBoard = s.enemyBoard.map {
                                if (it.instanceId == enemy.instanceId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
                            })
                        } else {
                            s.copy(playerBoard = s.playerBoard.map {
                                if (it.instanceId == enemy.instanceId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
                            })
                        }
                        logs.add("Khiên Phép của ${enemy.template.name} đã hóa giải Đấu Tay Đôi!")
                    } else {
                        val allyStrikeDmg = ally.strikePower
                        val enemyStrikeDmg = enemy.strikePower
                        s = performNonCombatStrike(
                            state = s,
                            strikerId = ally.instanceId,
                            strikerIsPlayer = sourceIsPlayer,
                            targetId = enemy.instanceId,
                            targetIsEnemy = sourceIsPlayer,
                            overrideDamage = allyStrikeDmg,
                            strikerSnapshot = ally,
                            deferStrikeHook = true,
                            logs = logs
                        )
                        s = performNonCombatStrike(
                            state = s,
                            strikerId = enemy.instanceId,
                            strikerIsPlayer = !sourceIsPlayer,
                            targetId = ally.instanceId,
                            targetIsEnemy = !sourceIsPlayer,
                            overrideDamage = enemyStrikeDmg,
                            strikerSnapshot = enemy,
                            deferStrikeHook = true,
                            logs = logs
                        )
                        if (allyStrikeDmg > 0) {
                            s = onUnitStrike(s, ally.instanceId, isPlayerStriker = sourceIsPlayer, hitNexus = false, logs = logs)
                        }
                        if (enemyStrikeDmg > 0) {
                            s = onUnitStrike(s, enemy.instanceId, isPlayerStriker = !sourceIsPlayer, hitNexus = false, logs = logs)
                        }
                        logs.add("Đấu Tay Đôi: ${ally.template.name} & ${enemy.template.name} giao đấu!")
                    }
                } else if (targetUnitId != null) {
                    logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                }
            }
            CardEffectType.PREDICT_DRAW -> {
                if (sourceIsPlayer) {
                    s = drawPlayerCard(s)
                    logs.add("Thủ Thuật Thời Gian Tiên Đoán và rút 1 lá.")
                } else {
                    s = drawEnemyCard(s)
                    logs.add("Thủ Thuật Thời Gian của địch rút 1 lá.")
                }
            }
            CardEffectType.PLANT_CHIMES -> {
                if (sourceIsPlayer) {
                    val updatedPile = s.playerDrawPile.toMutableList()
                    repeat(minOf(card.effectValue, updatedPile.size)) {
                        val idx = Random.nextInt(updatedPile.size)
                        updatedPile[idx] = updatedPile[idx].copy(hasChimeBoon = true)
                    }
                    s = s.copy(playerDrawPile = updatedPile)
                    if (s.playerBoard.isNotEmpty()) {
                        val top = s.playerBoard.first()
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == top.instanceId) it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1) else it
                        })
                    }
                    logs.add("Gieo ${card.effectValue} Chuông vào bộ bài!")
                }
            }
            CardEffectType.PLANT_PUFFCAPS -> {
                if (sourceIsPlayer) {
                    s = s.copy(enemyDeckTrapsCount = s.enemyDeckTrapsCount + card.effectValue)
                    logs.add("Gieo ${card.effectValue} Bẫy Nấm Độc vào bộ bài địch!")
                } else {
                    val updatedPile = s.playerDrawPile.toMutableList()
                    repeat(minOf(card.effectValue, updatedPile.size)) {
                        val idx = Random.nextInt(updatedPile.size)
                        updatedPile[idx] = updatedPile[idx].copy(hasPuffcapTrap = updatedPile[idx].hasPuffcapTrap + 1)
                    }
                    s = s.copy(playerDrawPile = updatedPile)
                    logs.add("Địch gieo ${card.effectValue} Nấm Độc vào bộ bài bạn!")
                }
            }
            CardEffectType.FROSTBITE_TWO -> {
                val targetIsEnemy = sourceIsPlayer
                val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
                val requestedIds = targetUnitId
                    ?.split("|")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    .orEmpty()
                if (targetBoard.isNotEmpty()) {
                    val targets = if (requestedIds.isNotEmpty()) {
                        requestedIds.mapNotNull { id -> targetBoard.find { it.instanceId == id } }.take(2)
                    } else {
                        targetBoard
                            .sortedWith(compareBy<UnitOnBoard> { it.isFrostbitten }.thenByDescending { it.effectivePower })
                            .take(2)
                    }
                    if (requestedIds.isNotEmpty() && targets.isEmpty()) {
                        logs.add("${card.name} bị mất mục tiêu nên phần Đóng Băng vô hiệu!")
                    } else {
                        targets.forEach { t ->
                            s = frostbiteUnit(s, t.instanceId, isEnemy = targetIsEnemy, isSpell = true, fromAllyEffect = false, logs = logs)
                        }
                    }
                }
                s = if (sourceIsPlayer) drawPlayerCard(s) else drawEnemyCard(s)
            }
            CardEffectType.ANIVIA_CHAMPION_SPELL -> {
                val targetIsEnemy = sourceIsPlayer
                val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
                val requestedIds = targetUnitId
                    ?.split("|")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    .orEmpty()
                if (targetBoard.isNotEmpty()) {
                    val targets = if (requestedIds.isNotEmpty()) {
                        requestedIds.mapNotNull { id -> targetBoard.find { it.instanceId == id } }.take(2)
                    } else {
                        targetBoard
                            .sortedWith(compareBy<UnitOnBoard> { it.isFrostbitten }.thenByDescending { it.effectivePower })
                            .take(2)
                    }
                    if (requestedIds.isNotEmpty() && targets.isEmpty()) {
                        logs.add("${card.name} bị mất mục tiêu nên phần Đóng Băng vô hiệu!")
                    } else {
                        targets.forEach { t ->
                            s = frostbiteUnit(s, t.instanceId, isEnemy = targetIsEnemy, isSpell = true, fromAllyEffect = false, logs = logs)
                        }
                    }
                }
                if (sourceIsPlayer) {
                    val aniviaCopy = createHandInstance(CardCatalog.getCard("card_anivia"), isFleeting = false, isCreated = true)
                    val pile = s.playerDrawPile.toMutableList()
                    val insertIdx = if (pile.isEmpty()) 0 else Random.nextInt(pile.size + 1)
                    pile.add(insertIdx, aniviaCopy)
                    s = s.copy(playerDrawPile = pile)
                    logs.add("Anivia: Cuồng Phong tráo 1 Anivia vào bộ bài!")
                }
            }
            CardEffectType.GLACIAL_STORM_SKILL -> {
                if (sourceIsPlayer) {
                    s.enemyBoard.toList().forEach { enemy ->
                        s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = dmg, isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    logs.add("${card.name} gây $dmg sát thương lên toàn bộ kẻ địch & Nhà Chính địch!")
                } else {
                    s.playerBoard.toList().forEach { ally ->
                        s = damageUnit(s, ally.instanceId, isEnemy = false, damage = dmg, isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                    s = dealDamageToPlayerNexus(s, dmg, logs)
                    logs.add("${card.name} của địch gây $dmg sát thương lên quân bạn & Nhà Chính!")
                }
            }
            CardEffectType.WINGS_OF_CRYOPHOENIX -> {
                val isExempt: (UnitOnBoard) -> Boolean = { u ->
                    u.template.isChampion || u.template.id == "card_wild_mystics"
                }
                if (sourceIsPlayer) {
                    s.enemyBoard.toList().filterNot(isExempt).forEach { enemy ->
                        s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = dmg, isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                    s.playerBoard.toList().filterNot(isExempt).forEach { ally ->
                        s = damageUnit(s, ally.instanceId, isEnemy = false, damage = dmg, isSpell = true, fromAllyEffect = true, logs = logs)
                    }
                    logs.add("Đôi Cánh Phượng Hoàng Băng gây $dmg sát thương lên tất cả bài quân (trừ Anh Hùng & Thầy Tu Hoang Dã).")
                } else {
                    s.playerBoard.toList().filterNot(isExempt).forEach { ally ->
                        s = damageUnit(s, ally.instanceId, isEnemy = false, damage = dmg, isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                    s.enemyBoard.toList().filterNot(isExempt).forEach { enemy ->
                        s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = dmg, isSpell = true, fromAllyEffect = true, logs = logs)
                    }
                }
            }
            CardEffectType.SUMMON_TWO_GIANTS -> {
                val count = maxOf(1, card.effectValue)
                if (sourceIsPlayer) {
                    repeat(count) {
                        if (s.playerBoard.size < 6) {
                            val giant = CardCatalog.giantFollowersPool.random()
                            s = summonPlayerUnit(s, createHandInstance(giant), logs).first
                        }
                    }
                    logs.add("Khổng Lồ Chạm Trán triệu hồi các tùy tùng Khổng Lồ Freljord!")
                } else {
                    repeat(count) {
                        if (s.enemyBoard.size < 6) {
                            val giant = CardCatalog.giantFollowersPool.random()
                            s = summonEnemyUnitFromPool(s, giant, spendMana = 0, logs = logs)
                        }
                    }
                }
            }
            CardEffectType.DRAIN -> {
                if (targetUnitId == "ENEMY_NEXUS" || (sourceIsPlayer && targetUnitId == "NEXUS")) {
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    s = applyLifesteal(s, forPlayer = sourceIsPlayer, dmg = dmg)
                    logs.add("${card.name} Hút Cạn $dmg sát thương từ Nhà Chính địch.")
                } else if (targetUnitId == "PLAYER_NEXUS" || (!sourceIsPlayer && targetUnitId == "NEXUS")) {
                    s = dealDamageToPlayerNexus(s, dmg, logs)
                    s = applyLifesteal(s, forPlayer = sourceIsPlayer, dmg = dmg)
                    logs.add("${card.name} của địch Hút Cạn $dmg sát thương từ Nhà Chính của bạn.")
                } else if (targetUnitId != null) {
                    val onEnemy = s.enemyBoard.any { it.instanceId == targetUnitId }
                    val onPlayer = s.playerBoard.any { it.instanceId == targetUnitId }
                    if (onEnemy || onPlayer) {
                        val targetIsEnemy = onEnemy
                        if (targetIsEnemy && !sourceIsPlayer) {
                            s = triggerFatedOnTarget(s, targetUnitId, sourceIsPlayer = false, logs = logs)
                        } else if (!targetIsEnemy && sourceIsPlayer) {
                            s = triggerFatedOnTarget(s, targetUnitId, sourceIsPlayer = true, logs = logs)
                        }
                        val fromAlly = (targetIsEnemy && !sourceIsPlayer) || (!targetIsEnemy && sourceIsPlayer)
                        val (afterHit, actualDealt) = damageUnitWithResult(
                            state = s,
                            unitId = targetUnitId,
                            isEnemy = targetIsEnemy,
                            damage = dmg,
                            isSpell = true,
                            fromAllyEffect = fromAlly,
                            logs = logs
                        )
                        s = applyLifesteal(afterHit, forPlayer = sourceIsPlayer, dmg = actualDealt)
                        logs.add("${card.name} Hút Cạn gây $actualDealt sát thương thực tế.")
                    } else {
                        logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                    }
                } else {
                    val target = if (sourceIsPlayer) s.enemyBoard.minByOrNull { it.health } else s.playerBoard.minByOrNull { it.health }
                    if (target != null) {
                        val (afterHit, actualDealt) = damageUnitWithResult(
                            state = s,
                            unitId = target.instanceId,
                            isEnemy = sourceIsPlayer,
                            damage = dmg,
                            isSpell = true,
                            fromAllyEffect = false,
                            logs = logs
                        )
                        s = applyLifesteal(afterHit, forPlayer = sourceIsPlayer, dmg = actualDealt)
                    } else {
                        if (sourceIsPlayer) s = dealDamageToEnemyNexus(s, dmg, logs)
                        else s = dealDamageToPlayerNexus(s, dmg, logs)
                        s = applyLifesteal(s, forPlayer = sourceIsPlayer, dmg = dmg)
                    }
                    logs.add("${card.name} kích hoạt Hút Cạn.")
                }
            }
            CardEffectType.RALLY -> {
                if (sourceIsPlayer) {
                    s = grantPlayerAttackToken(s, logs, card.name)
                } else {
                    s = s.copy(enemyHasAttackToken = true)
                }
                logs.add("${card.name} kích hoạt Tập Hợp!")
            }
            CardEffectType.BREAKOUT_EFFECT -> {
                val boost = maxOf(1, card.effectValue)
                if (sourceIsPlayer) {
                    val nextPow = s.roundTempPowerBuffs.toMutableMap()
                    val nextKws = s.roundTempKeywords.toMutableMap()
                    s = s.copy(
                        playerBoard = s.playerBoard.map { u ->
                            if (u.template.type != CardType.LANDMARK) {
                                nextPow[u.instanceId] = (nextPow[u.instanceId] ?: 0) + boost
                                if (!u.keywords.contains(Keyword.OVERWHELM)) {
                                    nextKws[u.instanceId] = (nextKws[u.instanceId] ?: emptySet()) + Keyword.OVERWHELM
                                }
                                u.copy(
                                    power = u.power + boost,
                                    keywords = u.keywords + Keyword.OVERWHELM
                                )
                            } else u
                        },
                        roundTempPowerBuffs = nextPow,
                        roundTempKeywords = nextKws
                    )
                    s = refreshEvolveAndSeenKeywords(s, logs)
                    logs.add("${card.name}: Toàn bộ đồng minh nhận +$boost|+0 và Áp Đảo trong vòng này!")
                } else {
                    val nextPow = s.roundTempPowerBuffs.toMutableMap()
                    val nextKws = s.roundTempKeywords.toMutableMap()
                    s = s.copy(
                        enemyBoard = s.enemyBoard.map { u ->
                            if (u.template.type != CardType.LANDMARK) {
                                nextPow[u.instanceId] = (nextPow[u.instanceId] ?: 0) + boost
                                if (!u.keywords.contains(Keyword.OVERWHELM)) {
                                    nextKws[u.instanceId] = (nextKws[u.instanceId] ?: emptySet()) + Keyword.OVERWHELM
                                }
                                u.copy(
                                    power = u.power + boost,
                                    keywords = u.keywords + Keyword.OVERWHELM
                                )
                            } else u
                        },
                        roundTempPowerBuffs = nextPow,
                        roundTempKeywords = nextKws
                    )
                }
            }
            CardEffectType.ASTRAL_DIFFUSION_EFFECT -> {
                val boost = maxOf(1, card.effectValue)
                if (sourceIsPlayer) {
                    s = s.copy(playerBoard = s.playerBoard.map { u ->
                        if (u.template.type != CardType.LANDMARK) {
                            val kw = Keyword.sharablePool.filterNot { it in u.keywords }.randomOrNull()
                                ?: Keyword.sharablePool.random()
                            u.copy(
                                power = u.power + boost,
                                health = u.health + boost,
                                maxHealth = u.maxHealth + boost,
                                keywords = u.keywords + kw
                            )
                        } else u
                    })
                    s = refreshEvolveAndSeenKeywords(s, logs)
                    logs.add("${card.name}: Toàn bộ đồng minh nhận +$boost|+$boost và 1 từ khóa ngẫu nhiên!")
                } else {
                    s = s.copy(enemyBoard = s.enemyBoard.map { u ->
                        if (u.template.type != CardType.LANDMARK) {
                            val kw = Keyword.sharablePool.random()
                            u.copy(
                                power = u.power + boost,
                                health = u.health + boost,
                                maxHealth = u.maxHealth + boost,
                                keywords = u.keywords + kw
                            )
                        } else u
                    })
                }
            }
            CardEffectType.STANCE_SWAP_EFFECT -> {
                val boost = maxOf(1, card.effectValue)
                val allyBoard = if (sourceIsPlayer) s.playerBoard else s.enemyBoard
                val chosen = targetUnitId?.let { id -> allyBoard.find { it.instanceId == id } }
                    ?: allyBoard.filter { it.template.type != CardType.LANDMARK }.maxByOrNull { it.effectivePower }
                if (chosen != null) {
                    if (targetUnitId != null) {
                        s = triggerFatedOnTarget(s, chosen.instanceId, sourceIsPlayer = sourceIsPlayer, logs = logs)
                    }
                    s = if (sourceIsPlayer) {
                        s.copy(playerBoard = s.playerBoard.map { u ->
                            if (u.instanceId == chosen.instanceId) {
                                u.copy(
                                    power = u.power + boost,
                                    health = u.health + boost,
                                    maxHealth = u.maxHealth + boost,
                                    keywords = u.keywords + Keyword.OVERWHELM
                                )
                            } else u
                        })
                    } else {
                        s.copy(enemyBoard = s.enemyBoard.map { u ->
                            if (u.instanceId == chosen.instanceId) {
                                u.copy(
                                    power = u.power + boost,
                                    health = u.health + boost,
                                    maxHealth = u.maxHealth + boost,
                                    keywords = u.keywords + Keyword.OVERWHELM
                                )
                            } else u
                        })
                    }
                    if (sourceIsPlayer) {
                        s = refreshEvolveAndSeenKeywords(s, logs)
                    }
                    logs.add("${card.name} ban +$boost|+$boost và Áp Đảo cho ${chosen.template.name}!")
                }
            }
            CardEffectType.BUFF_ALLIES_STATS -> {
                val boost = maxOf(0, card.effectValue)
                val targetBoard = if (sourceIsPlayer) s.playerBoard else s.enemyBoard
                val updatedBoard = targetBoard.map { u ->
                    if (u.template.type != CardType.LANDMARK) {
                        val kw = Keyword.sharablePool.filterNot { it in u.keywords }.randomOrNull()
                        u.copy(
                            power = u.power + boost,
                            health = u.health + boost,
                            maxHealth = u.maxHealth + boost,
                            keywords = if (kw != null) u.keywords + kw else u.keywords
                        )
                    } else u
                }
                s = if (sourceIsPlayer) s.copy(playerBoard = updatedBoard) else s.copy(enemyBoard = updatedBoard)
            }
            CardEffectType.FREE_ATTACK -> {
                val ownBoard = if (sourceIsPlayer) s.playerBoard else s.enemyBoard
                val chosen = targetUnitId?.let { id -> ownBoard.find { it.instanceId == id && it.canAttack } }
                    ?: ownBoard.filter { it.canAttack }.maxByOrNull { it.effectivePower }
                if (chosen != null) {
                    s = startFreeAttack(s, listOf(chosen.instanceId), playerIsAttacking = sourceIsPlayer, logs = logs)
                } else {
                    logs.add("${card.name} không có quân hợp lệ để Tấn Công Tự Do!")
                }
            }
            CardEffectType.PURGE_SUPPRESSION_CURSE, CardEffectType.PURGE_SHACKLES_CURSE, CardEffectType.PURGE_TERROR_CURSE -> {
                logs.add("Đã hóa giải Lời Nguyền ${card.name} khỏi tay.")
            }
            CardEffectType.SACRIFICE_DAMAGE -> {
                val parts = targetUnitId?.split("|")
                val allyId = parts?.getOrNull(0)
                val enemyId = parts?.getOrNull(1)
                val allyBoard = if (sourceIsPlayer) s.playerBoard else s.enemyBoard
                val allyToSacrifice = allyId?.let { id -> allyBoard.find { it.instanceId == id && it.template.type != CardType.LANDMARK } }
                    ?: allyBoard.filter { it.template.type != CardType.LANDMARK }.minByOrNull { it.power + it.health }
                if (allyToSacrifice == null) {
                    logs.add("${card.name} không có đồng minh để hy sinh nên vô hiệu!")
                } else {
                    s = removeUnit(s, allyToSacrifice.instanceId, isEnemy = !sourceIsPlayer, logs = logs)
                    logs.add("${card.name} hy sinh ${allyToSacrifice.template.name}!")
                    val oppBoard = if (sourceIsPlayer) s.enemyBoard else s.playerBoard
                    val targetEnemy = enemyId?.let { id -> oppBoard.find { it.instanceId == id } }
                        ?: oppBoard.filter { it.template.type != CardType.LANDMARK }.maxByOrNull { it.effectivePower }
                    if (targetEnemy != null) {
                        s = damageUnit(
                            state = s,
                            unitId = targetEnemy.instanceId,
                            isEnemy = sourceIsPlayer,
                            damage = dmg,
                            isSpell = true,
                            fromAllyEffect = false,
                            logs = logs
                        )
                        logs.add("${card.name} gây $dmg sát thương lên ${targetEnemy.template.name}!")
                    } else {
                        logs.add("${card.name} bị mất mục tiêu kẻ địch nên không gây sát thương!")
                    }
                }
            }
            CardEffectType.POSSESS_UNIT -> {
                val oppBoard = if (sourceIsPlayer) s.enemyBoard else s.playerBoard
                val ownBoard = if (sourceIsPlayer) s.playerBoard else s.enemyBoard
                val target = targetUnitId?.let { id -> oppBoard.find { it.instanceId == id } }
                    ?: oppBoard.filter { it.template.type != CardType.LANDMARK }.maxByOrNull { it.effectivePower }
                when {
                    target == null -> logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                    target.hasSpellShield -> {
                        s = if (sourceIsPlayer) {
                            s.copy(enemyBoard = s.enemyBoard.map {
                                if (it.instanceId == target.instanceId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
                            })
                        } else {
                            s.copy(playerBoard = s.playerBoard.map {
                                if (it.instanceId == target.instanceId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
                            })
                        }
                        logs.add("Khiên Phép của ${target.template.name} đã hóa giải ${card.name}!")
                    }
                    ownBoard.size >= 6 -> logs.add("Hàng chờ đã đầy, không thể Chiếm Hữu ${target.template.name}!")
                    else -> {
                        val origOwner = s.tempPossessedReturns[target.instanceId] ?: !sourceIsPlayer
                        val updatedReturns = if (origOwner == sourceIsPlayer) {
                            s.tempPossessedReturns - target.instanceId
                        } else {
                            s.tempPossessedReturns + (target.instanceId to origOwner)
                        }
                        val stolen = target.copy(ownerIsPlayer = sourceIsPlayer)
                        s = if (sourceIsPlayer) {
                            s.copy(
                                enemyBoard = s.enemyBoard.filterNot { it.instanceId == target.instanceId },
                                playerBoard = s.playerBoard + stolen,
                                tempPossessedReturns = updatedReturns
                            )
                        } else {
                            s.copy(
                                playerBoard = s.playerBoard.filterNot { it.instanceId == target.instanceId },
                                enemyBoard = s.enemyBoard + stolen,
                                tempPossessedReturns = updatedReturns
                            )
                        }
                        s = cleanCombatUnitReferences(s, target.instanceId, removeFromBlocksEvenIfCommitted = true)
                        logs.add("${card.name} chiếm quyền điều khiển ${target.template.name} trong vòng này!")
                    }
                }
            }
            CardEffectType.ELIXIR_OF_IRON -> {
                val allyBoard = if (sourceIsPlayer) s.playerBoard else s.enemyBoard
                val chosen = targetUnitId?.let { id -> allyBoard.find { it.instanceId == id } }
                    ?: allyBoard.filter { it.template.type != CardType.LANDMARK }.maxByOrNull { it.effectivePower }
                if (chosen != null) {
                    val hpGain = maxOf(1, card.effectValue)
                    if (targetUnitId != null) {
                        s = triggerFatedOnTarget(s, chosen.instanceId, sourceIsPlayer = sourceIsPlayer, logs = logs)
                    }
                    val nextHp = s.roundTempHealthBuffs + (chosen.instanceId to ((s.roundTempHealthBuffs[chosen.instanceId] ?: 0) + hpGain))
                    s = if (sourceIsPlayer) {
                        s.copy(
                            playerBoard = s.playerBoard.map { u ->
                                if (u.instanceId == chosen.instanceId) {
                                    applyPluckyPoroEmpowerment(
                                        u.copy(health = u.health + hpGain, maxHealth = u.maxHealth + hpGain)
                                    )
                                } else u
                            },
                            roundTempHealthBuffs = nextHp
                        )
                    } else {
                        s.copy(
                            enemyBoard = s.enemyBoard.map { u ->
                                if (u.instanceId == chosen.instanceId) {
                                    applyPluckyPoroEmpowerment(
                                        u.copy(health = u.health + hpGain, maxHealth = u.maxHealth + hpGain)
                                    )
                                } else u
                            },
                            roundTempHealthBuffs = nextHp
                        )
                    }
                    logs.add("${card.name}: Trao +0|+$hpGain cho ${chosen.template.name} trong vòng này!")
                } else if (targetUnitId != null) {
                    logs.add("${card.name} bị mất mục tiêu phần buff trên sân!")
                }
                if (sourceIsPlayer) {
                    s = grantStatsToDeckUnits(s, count = 1, powerGain = 1, healthGain = 1, logs = logs, sourceLabel = card.name)
                    s = refreshEvolveAndSeenKeywords(s, logs)
                }
            }
            CardEffectType.PHONG_HAN -> {
                val targetIsEnemy = sourceIsPlayer
                val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
                val target = targetUnitId?.let { id -> targetBoard.find { it.instanceId == id } }
                    ?: targetBoard.filter { !it.isFrostbitten }.maxByOrNull { it.effectivePower }
                    ?: targetBoard.maxByOrNull { it.effectivePower }
                if (targetUnitId != null && target == null) {
                    logs.add("${card.name} bị mất mục tiêu nên phần Đóng Băng & Kích Động vô hiệu!")
                } else if (target != null) {
                    val blockedBySpellShield = target.hasSpellShield
                    s = frostbiteUnit(s, target.instanceId, isEnemy = targetIsEnemy, isSpell = true, fromAllyEffect = false, logs = logs)
                    if (!blockedBySpellShield) {
                        val existingTempKws = s.roundTempKeywords[target.instanceId] ?: emptySet()
                        val addTemp = if (target.keywords.contains(Keyword.AGITATED)) emptySet() else setOf(Keyword.AGITATED)
                        val updatedBoard = (if (targetIsEnemy) s.enemyBoard else s.playerBoard).map { u ->
                            if (u.instanceId == target.instanceId) u.copy(keywords = u.keywords + Keyword.AGITATED) else u
                        }
                        s = if (targetIsEnemy) {
                            s.copy(
                                enemyBoard = updatedBoard,
                                roundTempKeywords = s.roundTempKeywords + (target.instanceId to (existingTempKws + addTemp))
                            )
                        } else {
                            s.copy(
                                playerBoard = updatedBoard,
                                roundTempKeywords = s.roundTempKeywords + (target.instanceId to (existingTempKws + addTemp))
                            )
                        }
                        logs.add("${card.name}: Đóng Băng và Kích Động ${target.template.name} trong vòng này!")
                    }
                }
                val poroTmpl = CardCatalog.getCard("card_lonely_poro")
                if (sourceIsPlayer && s.playerBoard.size < 6) {
                    s = summonPlayerUnit(s, createHandInstance(poroTmpl, isCreated = true), logs).first
                } else if (!sourceIsPlayer && s.enemyBoard.size < 6) {
                    s = summonEnemyUnitFromPool(s, poroTmpl, spendMana = 0, logs = logs)
                }
            }
            CardEffectType.TRYNDAMERE_CHAMPION_SPELL,
            CardEffectType.BATTLE_FURY -> {
                val targetBoard = if (sourceIsPlayer) s.playerBoard else s.enemyBoard
                val target = targetUnitId?.let { id -> targetBoard.find { it.instanceId == id && it.template.type != CardType.LANDMARK } }
                    ?: targetBoard.filter { it.template.type != CardType.LANDMARK }.maxByOrNull { it.effectivePower }
                if (target != null) {
                    if (targetUnitId != null) {
                        s = triggerFatedOnTarget(s, target.instanceId, sourceIsPlayer = sourceIsPlayer, logs = logs)
                    }
                    val updated = targetBoard.map { u ->
                        if (u.instanceId == target.instanceId) {
                            applyPluckyPoroEmpowerment(
                                u.copy(power = u.power + 8, health = u.health + 4, maxHealth = u.maxHealth + 4)
                            )
                        } else u
                    }
                    s = if (sourceIsPlayer) s.copy(playerBoard = updated) else s.copy(enemyBoard = updated)
                    logs.add("${card.name} ban +8|+4 cho ${target.template.name}!")
                }
                if (sourceIsPlayer) {
                    val tryndCopy = createHandInstance(CardCatalog.getCard("card_tryndamere"), isFleeting = false, isCreated = true)
                    val pile = s.playerDrawPile.toMutableList().apply {
                        add(if (isEmpty()) 0 else Random.nextInt(size + 1), tryndCopy)
                    }
                    s = s.copy(playerDrawPile = pile)
                    logs.add("${card.name} tạo 1 lá Tryndamere trong bộ bài!")
                }
            }
            CardEffectType.CALL_TO_ARMS_EFFECT,
            CardEffectType.CALL_TO_ARMS -> {
                if (sourceIsPlayer && s.playerHand.isNotEmpty()) {
                    s = s.copy(
                        pendingHandShuffleChoice = PendingHandShuffleChoice(
                            sourceSpellName = card.name,
                            candidateInstanceIds = s.playerHand.map { it.instanceId }
                        )
                    )
                }
            }
            else -> {}
        }
        s = refreshPluckyPoroEmpowerment(s)
        s = if (sourceIsPlayer) {
            s.copy(playerGraveyard = s.playerGraveyard + card)
        } else {
            s.copy(enemyGraveyard = s.enemyGraveyard + card)
        }
        return s
    }

    // --- ATTACK & BLOCK DECLARATION ---

    fun enterAttackDeclaration(state: CombatState): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.MAIN_ACTION_PHASE ||
            !state.playerHasAttackToken ||
            state.spellStack.isNotEmpty() ||
            state.attackingUnitIds.isNotEmpty() ||
            state.playerBoard.none { it.canAttack }
        ) return state
        return state.copy(
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            playerInitiatedCombat = true,
            blockCommitted = false,
            playerCastSpellInBlock = false,
            enemyCastBurstInBlock = false,
            playerFastSpellUsedInCombat = false,
            enemyFastSpellUsedInCombat = false,
            attackingUnitIds = emptyList(),
            blockPairings = emptyMap(),
            confirmedBlockedAttackerIds = emptySet(),
            challengedPairings = emptyMap(),
            lockedBlockAttackerIds = emptySet(),
            selectedAttackerForChallenger = null
        )
    }

    fun cancelAttackDeclaration(state: CombatState): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            state.spellStack.isNotEmpty()
        ) return state
        return state.copy(
            phase = CombatPhase.MAIN_ACTION_PHASE,
            attackingUnitIds = emptyList(),
            blockPairings = emptyMap(),
            confirmedBlockedAttackerIds = emptySet(),
            challengedPairings = emptyMap(),
            lockedBlockAttackerIds = emptySet(),
            selectedAttackerForChallenger = null,
            playerFastSpellUsedInCombat = false,
            enemyFastSpellUsedInCombat = false
        )
    }

    fun toggleUnitAttack(state: CombatState, unitId: String): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            !state.playerHasAttackToken ||
            !state.playerInitiatedCombat ||
            state.spellStack.isNotEmpty()
        ) return state
        val unit = state.playerBoard.find { it.instanceId == unitId } ?: return state
        if (!unit.canAttack) return state

        val current = state.attackingUnitIds.toMutableList()
        val updatedChallenges = state.challengedPairings.toMutableMap()
        var nextSelected = state.selectedAttackerForChallenger
        if (current.contains(unitId)) {
            current.remove(unitId)
            updatedChallenges.remove(unitId)
            if (nextSelected == unitId) nextSelected = null
        } else {
            current.add(unitId)
        }
        return state.copy(
            attackingUnitIds = current,
            challengedPairings = updatedChallenges,
            selectedAttackerForChallenger = nextSelected
        )
    }

    fun selectChallengerAttacker(state: CombatState, attackerId: String?): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            !state.playerHasAttackToken ||
            !state.playerInitiatedCombat ||
            state.spellStack.isNotEmpty()
        ) return state
        if (attackerId == null) return state.copy(selectedAttackerForChallenger = null)
        val unit = state.playerBoard.find { it.instanceId == attackerId } ?: return state
        val hasVulnerableTarget = state.enemyBoard.any {
            (it.keywords.contains(Keyword.VULNERABLE) || it.keywords.contains(Keyword.AGITATED)) &&
                it.template.type != CardType.LANDMARK &&
                !it.isStunned &&
                !it.isShackled
        }
        if ((!unit.keywords.contains(Keyword.CHALLENGER) && !hasVulnerableTarget) ||
            !state.attackingUnitIds.contains(attackerId)
        ) return state
        val toggled = if (state.selectedAttackerForChallenger == attackerId) null else attackerId
        return state.copy(selectedAttackerForChallenger = toggled)
    }

    fun assignChallengerTarget(state: CombatState, attackerId: String, enemyId: String): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            !state.playerHasAttackToken ||
            !state.playerInitiatedCombat ||
            state.spellStack.isNotEmpty()
        ) return state
        val atk = state.playerBoard.find { it.instanceId == attackerId } ?: return state
        val enemy = state.enemyBoard.find { it.instanceId == enemyId } ?: return state
        if (!state.attackingUnitIds.contains(attackerId)) return state
        if (!atk.keywords.contains(Keyword.CHALLENGER) &&
            !enemy.keywords.contains(Keyword.VULNERABLE) &&
            !enemy.keywords.contains(Keyword.AGITATED)
        ) return state
        if (enemy.template.type == CardType.LANDMARK || enemy.isStunned || enemy.isShackled) return state

        val updated = state.challengedPairings.toMutableMap()
        updated.entries.find { it.value == enemyId }?.let { updated.remove(it.key) }
        updated[attackerId] = enemyId
        return state.copy(
            challengedPairings = updated,
            selectedAttackerForChallenger = null
        )
    }

    fun clearChallengerTarget(state: CombatState, attackerId: String): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE
        ) return state
        val updated = state.challengedPairings.toMutableMap()
        updated.remove(attackerId)
        return state.copy(challengedPairings = updated)
    }

    fun commitPlayerAttack(state: CombatState): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            !state.playerHasAttackToken ||
            !state.playerInitiatedCombat ||
            state.attackingUnitIds.isEmpty()
        ) return state

        var s = state
        val logs = s.combatLog.toMutableList()
        val attackers = s.attackingUnitIds.mapNotNull { id -> s.playerBoard.find { it.instanceId == id } }

        // Check Scout Rally
        val allScout = attackers.all { it.keywords.contains(Keyword.SCOUT) }
        val scoutRally = allScout && !s.scoutUsedThisRound

        // 1. Lurk: check top of deck when a Lurker attacks (once per round)
        s = triggerPlayerLurkIfEligible(s, attackers, logs)

        // 3. Support: each attacker with Support buffs the ally immediately to its right (left-to-right)
        s = applyAttackSupportBuffs(s, s.attackingUnitIds, playerIsAttacking = true, logs = logs)

        // Guinsoo's Rageblade, Whirling Blade, Twin Drakehounds, Shock & Awe & Ashe Attack triggers
        attackers.forEach { atk ->
            if (atk.template.isChampion && s.equippedRelics.any { it.attackGainPlusOnePlusOne }) {
                s = s.copy(playerBoard = s.playerBoard.map {
                    if (it.instanceId == atk.instanceId) it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1) else it
                })
            }
            if (atk.template.isChampion && s.equippedRelics.any { it.id == "R0160" }) {
                s = dealDamageToEnemyNexus(s, 1, logs)
                s.enemyBoard.toList().forEach { e ->
                    s = damageUnit(s, e.instanceId, isEnemy = true, damage = 1, isSpell = false, logs = logs)
                }
            }
            if (atk.template.isChampion && s.equippedRelics.any { it.id == "R0120" }) {
                s = dealDamageToEnemyNexus(s, 2, logs)
            }
            if (atk.template.cost <= 1 && s.equippedRelics.any { it.id == "R0100" }) {
                s = dealDamageToEnemyNexus(s, 1, logs)
            }
            if (atk.template.id == "card_ashe" && !atk.isSilenced) {
                val target = s.enemyBoard.filter { !it.isFrostbitten }.maxByOrNull { it.effectivePower }
                    ?: s.enemyBoard.maxByOrNull { it.effectivePower }
                if (target != null) {
                    s = frostbiteUnit(s, target.instanceId, isEnemy = true, isSpell = false, fromAllyEffect = false, logs = logs)
                }
            }
            if (atk.template.id == "card_yadulsk_snowdog" && !atk.isSilenced) {
                s = grantStatsToDeckUnits(
                    state = s,
                    count = 2,
                    powerGain = 1,
                    healthGain = 1,
                    logs = logs,
                    sourceLabel = atk.template.name
                )
            }
        }
        if (attackers.isNotEmpty() && s.championId == "champ_tryndamere" && s.starLevel >= 2) {
            val allyIndices = s.playerDrawPile.mapIndexedNotNull { idx, c ->
                if (c.template.type == CardType.UNIT || c.template.type == CardType.CHAMPION) idx else null
            }
            if (allyIndices.isNotEmpty()) {
                val chosenIdx = allyIndices.random()
                val chosen = s.playerDrawPile[chosenIdx]
                val newPile = s.playerDrawPile.toMutableList().apply {
                    this[chosenIdx] = chosen.copy(bonusPower = chosen.bonusPower + 1, bonusHealth = chosen.bonusHealth + 1)
                }
                s = s.copy(playerDrawPile = newPile)
                logs.add("Say Máu (Tryndamere 2★): Ban +1|+1 cho ${chosen.template.name} trong bộ bài!")
            }
        }

        // Resolve any queued Fast spells before transitioning to defender's block phase
        if (s.spellStack.isNotEmpty()) {
            s = resolveSpellStack(s)
            if (s.playerWon != null) return s
        }

        // Pipeline 3.1: Đẩy Kỹ năng Nguyên Tố của Anivia lên Skill Stack khi tấn công
        val attackSkills = mutableListOf<SpellStackEntry>()
        attackers.forEach { atk ->
            val liveAtk = s.playerBoard.find { it.instanceId == atk.instanceId }
            if (liveAtk != null && liveAtk.template.id == "card_anivia" && !liveAtk.isSilenced && !liveAtk.isStunned) {
                val baseStormDmg = if (liveAtk.isLeveledUp) 2 else 1
                val skillCard = CardCatalog.getCard("skill_glacial_storm").copy(effectValue = baseStormDmg)
                s = onPlayerSkillOrElementalPlayed(s, logs, skillCard.name)
                attackSkills.add(
                    SpellStackEntry(
                        id = "st_${nextId()}",
                        sourceIsPlayer = true,
                        card = skillCard,
                        targetUnitId = null,
                        damageBonus = 0
                    )
                )
                logs.add("Kỹ năng ${skillCard.name} của Anivia (Lv${if (liveAtk.isLeveledUp) 2 else 1}) vào hàng chờ!")
            }
        }

        // Azir / Cythria / Sand Soldier attack triggers
        var extraAttackerIds = emptyList<String>()
        if (s.playerBoard.any { it.template.id == "card_azir" && !it.isSilenced } && s.playerBoard.size < 6) {
            val (afterSoldierState, soldierId) = summonPlayerUnit(s, createHandInstance(CardCatalog.getCard("card_sand_soldier")), logs)
            s = afterSoldierState
            if (soldierId != null) {
                extraAttackerIds = extraAttackerIds + soldierId
            }
        }
        val sandSoldierAtkCount = attackers.count { it.template.id == "card_sand_soldier" && !it.isSilenced } + extraAttackerIds.size
        if (sandSoldierAtkCount > 0) {
            s = dealDamageToEnemyNexus(s, sandSoldierAtkCount, logs)
            logs.add("Lính Cát gây $sandSoldierAtkCount sát thương lên Nhà Chính địch!")
        }
        if (attackers.any { it.template.id == "card_cythria_the_bold" && !it.isSilenced }) {
            val cythriaId = attackers.first { it.template.id == "card_cythria_the_bold" }.instanceId
            val nextPow = s.roundTempPowerBuffs.toMutableMap()
            val nextHp = s.roundTempHealthBuffs.toMutableMap()
            val nextKws = s.roundTempKeywords.toMutableMap()
            s = s.copy(
                playerBoard = s.playerBoard.map { u ->
                    if (u.instanceId != cythriaId && u.template.type != CardType.LANDMARK) {
                        nextPow[u.instanceId] = (nextPow[u.instanceId] ?: 0) + 1
                        nextHp[u.instanceId] = (nextHp[u.instanceId] ?: 0) + 1
                        if (!u.keywords.contains(Keyword.FEARSOME)) {
                            nextKws[u.instanceId] = (nextKws[u.instanceId] ?: emptySet()) + Keyword.FEARSOME
                        }
                        u.copy(power = u.power + 1, health = u.health + 1, maxHealth = u.maxHealth + 1, keywords = u.keywords + Keyword.FEARSOME)
                    } else u
                },
                roundTempPowerBuffs = nextPow,
                roundTempHealthBuffs = nextHp,
                roundTempKeywords = nextKws
            )
            logs.add("Cithria Hùng Mạnh ban +1|+1 và Đáng Sợ cho đồng minh!")
        }

        val survivingAttackerIds = (s.attackingUnitIds + extraAttackerIds).distinct().filter { id ->
            s.playerBoard.any { it.instanceId == id && !it.isStunned }
        }
        val lockedBlocks = s.challengedPairings.filter { (atkId, enemyId) ->
            survivingAttackerIds.contains(atkId) &&
                s.enemyBoard.any { it.instanceId == enemyId && !it.isStunned && !it.isShackled }
        }

        logs.add("Tuyên bố tấn công với ${survivingAttackerIds.size} quân! Địch đang chọn quân chặn...")
        return s.copy(
            playerHasAttackToken = false,
            scoutUsedThisRound = s.scoutUsedThisRound || allScout,
            pendingScoutRally = scoutRally,
            playerInitiatedCombat = true,
            blockCommitted = false,
            playerCastSpellInBlock = false,
            enemyCastBurstInBlock = false,
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.ENEMY,
            consecutivePasses = 0,
            lastStackCaster = if (attackSkills.isNotEmpty()) PriorityHolder.PLAYER else s.lastStackCaster,
            spellStack = s.spellStack + attackSkills,
            attackingUnitIds = survivingAttackerIds,
            blockPairings = lockedBlocks,
            confirmedBlockedAttackerIds = emptySet(),
            challengedPairings = emptyMap(),
            lockedBlockAttackerIds = lockedBlocks.keys.toSet(),
            selectedAttackerForChallenger = null,
            selectedEnemyAttackerForBlock = null,
            combatLog = logs.takeLast(16)
        )
    }

    fun assignPlayerBlocker(state: CombatState, attackerId: String, blockerId: String): CombatState {
        if (state.phase != CombatPhase.COMBAT_DECLARE_BLOCK_PHASE ||
            state.priorityHolder != PriorityHolder.PLAYER ||
            state.playerInitiatedCombat ||
            state.blockCommitted ||
            state.lockedBlockAttackerIds.contains(attackerId)
        ) return state
        val attacker = state.enemyBoard.find { it.instanceId == attackerId } ?: return state
        val blocker = state.playerBoard.find { it.instanceId == blockerId } ?: return state
        if (!blocker.canBlockAttacker(attacker)) return state

        val updated = state.blockPairings.toMutableMap()
        val prevKey = updated.entries.find { it.value == blockerId }?.key
        if (prevKey != null) {
            if (state.lockedBlockAttackerIds.contains(prevKey)) return state
            updated.remove(prevKey)
        }
        updated[attackerId] = blockerId
        return state.copy(blockPairings = updated, selectedEnemyAttackerForBlock = null)
    }

    fun removePlayerBlocker(state: CombatState, attackerId: String): CombatState {
        if (state.phase != CombatPhase.COMBAT_DECLARE_BLOCK_PHASE ||
            state.priorityHolder != PriorityHolder.PLAYER ||
            state.playerInitiatedCombat ||
            state.blockCommitted ||
            state.lockedBlockAttackerIds.contains(attackerId)
        ) return state
        val updated = state.blockPairings.toMutableMap()
        updated.remove(attackerId)
        return state.copy(blockPairings = updated)
    }

    fun commitPlayerBlock(state: CombatState): CombatState {
        if (state.phase != CombatPhase.COMBAT_DECLARE_BLOCK_PHASE || state.priorityHolder != PriorityHolder.PLAYER) return state
        if (state.playerInitiatedCombat) {
            if (state.spellStack.isNotEmpty()) {
                return resolveSpellStack(state)
            }
            return finishPlayerAttackCombat(state)
        }
        if (!state.blockCommitted && state.spellStack.isEmpty()) {
            val logs = state.combatLog.toMutableList()
            logs.add("Đã chốt đội hình chặn — Địch có thể phản hồi phép.")
            return state.copy(
                blockCommitted = true,
                confirmedBlockedAttackerIds = state.blockPairings.keys.toSet(),
                priorityHolder = PriorityHolder.ENEMY,
                consecutivePasses = 0,
                selectedEnemyAttackerForBlock = null,
                combatLog = logs.takeLast(16)
            )
        }
        return finishEnemyAttackCombat(state)
    }

    internal fun finishPlayerAttackCombat(state: CombatState): CombatState {
        var s = state.copy(blockCommitted = false)
        if (s.spellStack.isNotEmpty()) {
            s = resolveSpellStack(s)
            if (s.playerWon != null) return s
        }
        val logs = s.combatLog.toMutableList()
        s = resolveCombatResolution(s, playerIsAttacking = true, logs = logs)
        val scoutRally = s.pendingScoutRally
        val postCombatPriority = if (scoutRally) PriorityHolder.PLAYER else PriorityHolder.ENEMY
        if (scoutRally) {
            s = grantPlayerAttackToken(s, logs, "Trinh Sát")
        }
        return checkGameOver(
            s.copy(
                pendingScoutRally = false,
                isFreeAttack = false,
                blockCommitted = false,
                playerCastSpellInBlock = false,
                enemyCastBurstInBlock = false,
                playerFastSpellUsedInCombat = false,
                enemyFastSpellUsedInCombat = false,
                phase = if (s.playerWon != null) CombatPhase.GAME_OVER else CombatPhase.MAIN_ACTION_PHASE,
                priorityHolder = postCombatPriority,
                consecutivePasses = 0,
                lastStackCaster = null,
                attackingUnitIds = emptyList(),
                blockPairings = emptyMap(),
                confirmedBlockedAttackerIds = emptySet(),
                challengedPairings = emptyMap(),
                lockedBlockAttackerIds = emptySet(),
                selectedAttackerForChallenger = null,
                selectedEnemyAttackerForBlock = null,
                combatLog = logs.takeLast(16)
            )
        )
    }

    internal fun finishEnemyAttackCombat(state: CombatState): CombatState {
        var s = state.copy(blockCommitted = false)
        if (s.spellStack.isNotEmpty()) {
            s = resolveSpellStack(s)
            if (s.playerWon != null) return s
        }
        val logs = s.combatLog.toMutableList()
        s = resolveCombatResolution(s, playerIsAttacking = false, logs = logs)
        val nextEnemyToken = if (s.isFreeAttack) s.enemyHasAttackToken else false
        return checkGameOver(
            s.copy(
                enemyHasAttackToken = nextEnemyToken,
                isFreeAttack = false,
                blockCommitted = false,
                playerCastSpellInBlock = false,
                enemyCastBurstInBlock = false,
                playerFastSpellUsedInCombat = false,
                enemyFastSpellUsedInCombat = false,
                phase = if (s.playerWon != null) CombatPhase.GAME_OVER else CombatPhase.MAIN_ACTION_PHASE,
                priorityHolder = PriorityHolder.PLAYER,
                consecutivePasses = 0,
                lastStackCaster = null,
                attackingUnitIds = emptyList(),
                blockPairings = emptyMap(),
                confirmedBlockedAttackerIds = emptySet(),
                challengedPairings = emptyMap(),
                lockedBlockAttackerIds = emptySet(),
                selectedAttackerForChallenger = null,
                selectedEnemyAttackerForBlock = null,
                combatLog = logs.takeLast(16)
            )
        )
    }

    internal fun resolveCombatResolution(
        state: CombatState,
        playerIsAttacking: Boolean,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        val attackers = s.attackingUnitIds.toList()

        // Compute strike power dynamically against current state so earlier left-to-right pair results take effect immediately
        fun strikePowerOf(currState: CombatState, unit: UnitOnBoard, isPlayersUnit: Boolean): Int {
            val aura = if (isPlayersUnit && !unit.isFrostbitten && !unit.keywords.contains(Keyword.FORMIDABLE) &&
                currState.championId == "champ_ashe" && currState.starLevel >= 2
            ) {
                currState.enemyBoard.count { it.isFrostbitten }
            } else 0
            return unit.strikePower + aura
        }

        fun applyOverwhelmOrStandardStrike(
            currState: CombatState,
            attacker: UnitOnBoard,
            defender: UnitOnBoard,
            strikeDmg: Int
        ): Pair<CombatState, Boolean> {
            var st = currState
            if (strikeDmg <= 0) return st to false

            val defHadBarrier = defender.hasBarrier
            val defHasTough = defender.keywords.contains(Keyword.TOUGH)
            val defHasAgitated = defender.keywords.contains(Keyword.AGITATED)
            val hasOverwhelm = attacker.keywords.contains(Keyword.OVERWHELM)

            var nexusOverflow = 0
            val damageAssignedToBlocker = if (hasOverwhelm) {
                // Barrier absorbs damage before Tough/Agitated apply
                val toughOffset = if (!defHadBarrier && defHasTough) 1 else 0
                val agitatedOffset = if (!defHadBarrier && defHasAgitated) 1 else 0
                val lethalNeeded = maxOf(1, defender.health + toughOffset - agitatedOffset)
                nexusOverflow = maxOf(0, strikeDmg - lethalNeeded)
                minOf(strikeDmg, lethalNeeded)
            } else {
                strikeDmg
            }

            val (afterHit, actualDealtToBlocker) = damageUnitWithResult(
                state = st,
                unitId = defender.instanceId,
                isEnemy = playerIsAttacking,
                damage = damageAssignedToBlocker,
                isSpell = false,
                fromAllyEffect = false,
                logs = logs
            )
            st = afterHit

            if (nexusOverflow > 0) {
                st = if (playerIsAttacking) {
                    dealDamageToEnemyNexus(st, nexusOverflow, logs)
                } else {
                    dealDamageToPlayerNexus(st, nexusOverflow, logs)
                }
            }

            val totalDealt = actualDealtToBlocker + nexusOverflow
            if (attacker.keywords.contains(Keyword.LIFESTEAL) && totalDealt > 0) {
                st = applyLifesteal(st, forPlayer = playerIsAttacking, dmg = totalDealt)
            }

            val defStillOnBoard = (if (playerIsAttacking) st.enemyBoard else st.playerBoard)
                .any { it.instanceId == defender.instanceId }
            if (!defStillOnBoard) {
                st = applyKillBonuses(st, attacker.instanceId, isPlayerKiller = playerIsAttacking, logs = logs)
            }

            if (attacker.impactStacks > 0) {
                st = if (playerIsAttacking) {
                    dealDamageToEnemyNexus(st, attacker.impactStacks, logs)
                } else {
                    dealDamageToPlayerNexus(st, attacker.impactStacks, logs)
                }
                logs.add("${attacker.template.name} Khí Lực gây ${attacker.impactStacks} sát thương Nhà Chính!")
            }

            return st to (nexusOverflow > 0)
        }

        for (atkId in attackers) {
            val atk = (if (playerIsAttacking) s.playerBoard else s.enemyBoard).find { it.instanceId == atkId } ?: continue
            if (atk.isStunned) continue
            val blkId = s.blockPairings[atkId]
            val blk = blkId?.let { id -> (if (playerIsAttacking) s.enemyBoard else s.playerBoard).find { it.instanceId == id } }
            val activeBlk = blk?.takeIf { !it.isStunned }
            val wasBlocked = s.confirmedBlockedAttackerIds.contains(atkId) || blkId != null

            val initialAtkDmg = strikePowerOf(s, atk, playerIsAttacking)
            var attackerStruck = false
            var blockerStruck = false

            if (!wasBlocked && activeBlk == null) {
                // Unblocked strike directly to Nexus
                if (initialAtkDmg > 0) {
                    attackerStruck = true
                    val strikes = if (atk.keywords.contains(Keyword.DOUBLE_ATTACK)) 2 else 1
                    var totalNexusDmg = 0
                    repeat(strikes) {
                        val liveAtk = (if (playerIsAttacking) s.playerBoard else s.enemyBoard).find { it.instanceId == atkId }
                            ?: return@repeat
                        val dmg = strikePowerOf(s, liveAtk, playerIsAttacking)
                        if (dmg > 0) {
                            totalNexusDmg += dmg
                            if (playerIsAttacking) {
                                s = dealDamageToEnemyNexus(s, dmg, logs)
                            } else {
                                s = dealDamageToPlayerNexus(s, dmg, logs)
                            }
                            if (liveAtk.keywords.contains(Keyword.LIFESTEAL)) {
                                s = applyLifesteal(s, forPlayer = playerIsAttacking, dmg = dmg)
                            }
                            if (liveAtk.impactStacks > 0) {
                                s = if (playerIsAttacking) {
                                    dealDamageToEnemyNexus(s, liveAtk.impactStacks, logs)
                                } else {
                                    dealDamageToPlayerNexus(s, liveAtk.impactStacks, logs)
                                }
                                logs.add("${liveAtk.template.name} Khí Lực gây ${liveAtk.impactStacks} sát thương Nhà Chính!")
                            }
                            s = onUnitStrike(s, atkId, isPlayerStriker = playerIsAttacking, hitNexus = true, logs = logs)
                        }
                    }
                    logs.add("${atk.template.name} đánh thẳng Nhà Chính gây $totalNexusDmg sát thương!")
                }
            } else if (activeBlk == null) {
                // Phantom / Ghost block: blocker died, recalled, obliterated, captured, or stunned after block confirmation
                if (atk.keywords.contains(Keyword.OVERWHELM)) {
                    if (initialAtkDmg > 0) {
                        attackerStruck = true
                        val strikes = if (atk.keywords.contains(Keyword.DOUBLE_ATTACK)) 2 else 1
                        var totalNexusDmg = 0
                        repeat(strikes) {
                            val liveAtk = (if (playerIsAttacking) s.playerBoard else s.enemyBoard).find { it.instanceId == atkId }
                                ?: return@repeat
                            val dmg = strikePowerOf(s, liveAtk, playerIsAttacking)
                            if (dmg > 0) {
                                totalNexusDmg += dmg
                                if (playerIsAttacking) {
                                    s = dealDamageToEnemyNexus(s, dmg, logs)
                                } else {
                                    s = dealDamageToPlayerNexus(s, dmg, logs)
                                }
                                if (liveAtk.keywords.contains(Keyword.LIFESTEAL)) {
                                    s = applyLifesteal(s, forPlayer = playerIsAttacking, dmg = dmg)
                                }
                                if (liveAtk.impactStacks > 0) {
                                    s = if (playerIsAttacking) {
                                        dealDamageToEnemyNexus(s, liveAtk.impactStacks, logs)
                                    } else {
                                        dealDamageToPlayerNexus(s, liveAtk.impactStacks, logs)
                                    }
                                    logs.add("${liveAtk.template.name} Khí Lực gây ${liveAtk.impactStacks} sát thương Nhà Chính!")
                                }
                                s = onUnitStrike(s, atkId, isPlayerStriker = playerIsAttacking, hitNexus = true, logs = logs)
                            }
                        }
                        logs.add("${atk.template.name} (Áp Đảo) xuyên thẳng Nhà Chính gây $totalNexusDmg sát thương!")
                    }
                } else {
                    logs.add("${atk.template.name} đã bị chặn trước đó nên không đánh Nhà Chính.")
                }
            } else {
                // Combat between Attacker and active Blocker
                val hasFirstStrike = atk.keywords.contains(Keyword.QUICK_ATTACK) || atk.keywords.contains(Keyword.DOUBLE_ATTACK)
                val strikesCount = if (atk.keywords.contains(Keyword.DOUBLE_ATTACK)) 2 else 1

                if (hasFirstStrike) {
                    if (initialAtkDmg > 0) {
                        attackerStruck = true
                        val (afterFirst, hitNexus1) = applyOverwhelmOrStandardStrike(s, atk, activeBlk, initialAtkDmg)
                        s = afterFirst
                        s = onUnitStrike(s, atk.instanceId, isPlayerStriker = playerIsAttacking, hitNexus = hitNexus1, logs = logs)
                    }

                    val survivingBlk = (if (playerIsAttacking) s.enemyBoard else s.playerBoard)
                        .find { it.instanceId == activeBlk.instanceId && !it.isStunned }
                    val survivingAtkAfter1 = (if (playerIsAttacking) s.playerBoard else s.enemyBoard)
                        .find { it.instanceId == atk.instanceId && !it.isStunned }

                    if (survivingBlk != null) {
                        // Capture blocker's simultaneous strike power BEFORE Strike 2 hits
                        val blkDmg = strikePowerOf(s, survivingBlk, !playerIsAttacking)
                        var strike2Executed = false
                        var hitNexus2 = false

                        if (strikesCount == 2 && survivingAtkAfter1 != null) {
                            val secondAtkDmg = strikePowerOf(s, survivingAtkAfter1, playerIsAttacking)
                            if (secondAtkDmg > 0) {
                                attackerStruck = true
                                strike2Executed = true
                                val (afterStrike2, h2) = applyOverwhelmOrStandardStrike(s, survivingAtkAfter1, survivingBlk, secondAtkDmg)
                                s = afterStrike2
                                hitNexus2 = h2
                            }
                        }

                        if (blkDmg > 0 && survivingAtkAfter1 != null) {
                            blockerStruck = true
                            val (afterBlkHit, actualBlkDealt) = damageUnitWithResult(
                                state = s,
                                unitId = survivingAtkAfter1.instanceId,
                                isEnemy = !playerIsAttacking,
                                damage = blkDmg,
                                isSpell = false,
                                fromAllyEffect = false,
                                logs = logs
                            )
                            s = afterBlkHit
                            if (survivingBlk.keywords.contains(Keyword.LIFESTEAL) && actualBlkDealt > 0) {
                                s = applyLifesteal(s, forPlayer = !playerIsAttacking, dmg = actualBlkDealt)
                            }
                            val atkAfterReturn = (if (playerIsAttacking) s.playerBoard else s.enemyBoard)
                                .find { it.instanceId == survivingAtkAfter1.instanceId }
                            if (atkAfterReturn == null) {
                                s = applyKillBonuses(s, survivingBlk.instanceId, isPlayerKiller = !playerIsAttacking, logs = logs)
                            }
                        }

                        if (strike2Executed && survivingAtkAfter1 != null) {
                            s = onUnitStrike(s, survivingAtkAfter1.instanceId, isPlayerStriker = playerIsAttacking, hitNexus = hitNexus2, logs = logs)
                        }
                        if (blockerStruck) {
                            s = onUnitStrike(s, survivingBlk.instanceId, isPlayerStriker = !playerIsAttacking, hitNexus = false, logs = logs)
                        }
                    } else if (strikesCount == 2 && survivingAtkAfter1 != null) {
                        // Blocker died on Strike 1 -> Strike 2 only hits Nexus if Attacker has Overwhelm
                        val secondAtkDmg = strikePowerOf(s, survivingAtkAfter1, playerIsAttacking)
                        if (secondAtkDmg > 0 && survivingAtkAfter1.keywords.contains(Keyword.OVERWHELM)) {
                            if (playerIsAttacking) {
                                s = dealDamageToEnemyNexus(s, secondAtkDmg, logs)
                            } else {
                                s = dealDamageToPlayerNexus(s, secondAtkDmg, logs)
                            }
                            if (survivingAtkAfter1.keywords.contains(Keyword.LIFESTEAL)) {
                                s = applyLifesteal(s, forPlayer = playerIsAttacking, dmg = secondAtkDmg)
                            }
                            if (survivingAtkAfter1.impactStacks > 0) {
                                s = if (playerIsAttacking) {
                                    dealDamageToEnemyNexus(s, survivingAtkAfter1.impactStacks, logs)
                                } else {
                                    dealDamageToPlayerNexus(s, survivingAtkAfter1.impactStacks, logs)
                                }
                            }
                            s = onUnitStrike(s, survivingAtkAfter1.instanceId, isPlayerStriker = playerIsAttacking, hitNexus = true, logs = logs)
                        }
                    }
                } else {
                    // Simultaneous normal combat: snapshot both strike powers first
                    val atkDmg = initialAtkDmg
                    val blkDmg = strikePowerOf(s, activeBlk, !playerIsAttacking)
                    var atkHitNexus = false

                    if (atkDmg > 0) {
                        attackerStruck = true
                        val (afterAtk, h) = applyOverwhelmOrStandardStrike(s, atk, activeBlk, atkDmg)
                        s = afterAtk
                        atkHitNexus = h
                    }
                    if (blkDmg > 0) {
                        blockerStruck = true
                        val (afterBlkHit, actualBlkDealt) = damageUnitWithResult(
                            state = s,
                            unitId = atk.instanceId,
                            isEnemy = !playerIsAttacking,
                            damage = blkDmg,
                            isSpell = false,
                            fromAllyEffect = false,
                            logs = logs
                        )
                        s = afterBlkHit
                        if (activeBlk.keywords.contains(Keyword.LIFESTEAL) && actualBlkDealt > 0) {
                            s = applyLifesteal(s, forPlayer = !playerIsAttacking, dmg = actualBlkDealt)
                        }
                        val atkSurvived = (if (playerIsAttacking) s.playerBoard else s.enemyBoard)
                            .find { it.instanceId == atk.instanceId }
                        if (atkSurvived == null) {
                            s = applyKillBonuses(s, activeBlk.instanceId, isPlayerKiller = !playerIsAttacking, logs = logs)
                        }
                    }
                    if (attackerStruck) {
                        s = onUnitStrike(s, atk.instanceId, isPlayerStriker = playerIsAttacking, hitNexus = atkHitNexus, logs = logs)
                    }
                    if (blockerStruck) {
                        s = onUnitStrike(s, activeBlk.instanceId, isPlayerStriker = !playerIsAttacking, hitNexus = false, logs = logs)
                    }
                }
            }

            // Sức Mạnh Địch: Phân Thân Bóng Tối (Zed Trùm Phụ)
            if (!playerIsAttacking && canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle) == "LIVING_SHADOW") {
                val survivingAtk = s.enemyBoard.find { it.instanceId == atkId }
                if (survivingAtk != null && !survivingAtk.keywords.contains(Keyword.EPHEMERAL)) {
                    s = s.copy(enemyBoard = s.enemyBoard.map { u ->
                        if (u.instanceId == atkId) {
                            u.copy(power = u.power + 1, health = u.health + 1, maxHealth = u.maxHealth + 1)
                        } else u
                    })
                    logs.add("Phân Thân Bóng Tối cường hóa ${survivingAtk.template.name} (+1|+1)!")
                }
            }

            // Ephemeral attacker or blocker dies ONLY after actually striking
            val postAtkBoard = if (playerIsAttacking) s.playerBoard else s.enemyBoard
            val postAtk = postAtkBoard.find { it.instanceId == atkId }
            if (attackerStruck && postAtk != null && !postAtk.isStunned && postAtk.keywords.contains(Keyword.EPHEMERAL)) {
                s = removeUnit(s, postAtk.instanceId, isEnemy = !playerIsAttacking, logs = logs)
            }
            if (activeBlk != null) {
                val postBlkBoard = if (playerIsAttacking) s.enemyBoard else s.playerBoard
                val postBlk = postBlkBoard.find { it.instanceId == activeBlk.instanceId }
                if (blockerStruck && postBlk != null && !postBlk.isStunned && postBlk.keywords.contains(Keyword.EPHEMERAL)) {
                    s = removeUnit(s, postBlk.instanceId, isEnemy = playerIsAttacking, logs = logs)
                }
            }
            if (s.phase == CombatPhase.GAME_OVER) break
        }

        // Garen 1★/3★ strike scaling
        if (s.championId == "champ_garen" && s.starLevel >= 1 && playerIsAttacking) {
            val bonus = if (s.starLevel >= 3) 2 else 1
            s = s.copy(playerBoard = s.playerBoard.map { u ->
                if (attackers.contains(u.instanceId)) {
                    u.copy(power = u.power + bonus, health = u.health + bonus, maxHealth = u.maxHealth + bonus)
                } else u
            })
            if (s.starLevel >= 3 && attackers.isNotEmpty() && s.playerHand.size < 10) {
                s = s.copy(playerHand = s.playerHand + createHandInstance(CardCatalog.getCard("card_single_combat"), isFleeting = true))
                logs.add("Garen 3★ tạo Đấu Tay Đôi Chóng Tàn!")
            }
        }

        return checkGameOver(s)
    }

    internal fun performNonCombatStrike(
        state: CombatState,
        strikerId: String,
        strikerIsPlayer: Boolean,
        targetId: String,
        targetIsEnemy: Boolean,
        overrideDamage: Int? = null,
        strikerSnapshot: UnitOnBoard? = null,
        deferStrikeHook: Boolean = false,
        logs: MutableList<String>
    ): CombatState {
        val strikerBoard = if (strikerIsPlayer) state.playerBoard else state.enemyBoard
        val striker = strikerBoard.find { it.instanceId == strikerId } ?: strikerSnapshot ?: return state
        val dmg = overrideDamage ?: striker.strikePower
        if (dmg <= 0) return state

        val (afterHit, actualDealt) = damageUnitWithResult(
            state = state,
            unitId = targetId,
            isEnemy = targetIsEnemy,
            damage = dmg,
            isSpell = false,
            fromAllyEffect = false,
            logs = logs
        )
        var s = afterHit
        if (striker.keywords.contains(Keyword.LIFESTEAL) && actualDealt > 0) {
            s = applyLifesteal(s, forPlayer = strikerIsPlayer, dmg = actualDealt)
        }
        val targetSurvived = (if (targetIsEnemy) s.enemyBoard else s.playerBoard).any { it.instanceId == targetId }
        if (!targetSurvived) {
            s = applyKillBonuses(s, strikerId, isPlayerKiller = strikerIsPlayer, logs = logs)
        }
        if (!deferStrikeHook) {
            s = onUnitStrike(s, strikerId, isPlayerStriker = strikerIsPlayer, hitNexus = false, logs = logs)
        }
        return s
    }

    private fun applyKillBonuses(
        state: CombatState,
        killerId: String,
        isPlayerKiller: Boolean,
        logs: MutableList<String>
    ): CombatState {
        val board = if (isPlayerKiller) state.playerBoard else state.enemyBoard
        var fioraKillsReached = 0
        var fioraInstantWin = false
        val updated = board.map { u ->
            if (u.instanceId != killerId || u.isSilenced) {
                u
            } else {
                var nextUnit = u
                if (nextUnit.keywords.contains(Keyword.FURY)) {
                    logs.add("${nextUnit.template.name} kích hoạt Cuồng Nộ (+1|+1)!")
                    nextUnit = nextUnit.copy(
                        power = nextUnit.power + 1,
                        health = nextUnit.health + 1,
                        maxHealth = nextUnit.maxHealth + 1
                    )
                }
                if (nextUnit.template.id == "card_fiora") {
                    val nextKills = nextUnit.killCount + 1
                    fioraKillsReached = nextKills
                    nextUnit = nextUnit.copy(killCount = nextKills)
                    logs.add("Fiora hạ gục kẻ địch ($nextKills/4)!")
                    if (!nextUnit.isLeveledUp && nextKills >= 2) {
                        nextUnit = nextUnit.copy(
                            isLeveledUp = true,
                            power = nextUnit.power + nextUnit.template.levelUpPowerBonus,
                            health = nextUnit.health + nextUnit.template.levelUpHealthBonus,
                            maxHealth = nextUnit.maxHealth + nextUnit.template.levelUpHealthBonus
                        )
                        logs.add("★ Fiora ĐÃ THĂNG CẤP! (+${nextUnit.template.levelUpPowerBonus}|+${nextUnit.template.levelUpHealthBonus})")
                    }
                    if (nextKills >= 4) {
                        fioraInstantWin = true
                    }
                }
                nextUnit
            }
        }
        var s = if (isPlayerKiller) state.copy(playerBoard = updated) else state.copy(enemyBoard = updated)
        val killer = updated.find { it.instanceId == killerId }
        if (killer != null && !killer.isSilenced && killer.template.id == "card_camavoran_dragon") {
            s = if (isPlayerKiller) {
                dealDamageToEnemyNexus(s, 1, logs)
            } else {
                dealDamageToPlayerNexus(s, 1, logs)
            }
            s = applyLifesteal(s, forPlayer = isPlayerKiller, dmg = 1)
            logs.add("${killer.template.name} tiêu diệt kẻ địch, hút 1 Máu từ Nhà Chính đối phương!")
        }
        if (fioraInstantWin && fioraKillsReached >= 4) {
            s = if (isPlayerKiller) {
                logs.add("★ Fiora đạt 4/4 hạ gục — lập tức trừ hết máu Nhà Chính địch!")
                s.copy(enemyNexusHp = 0)
            } else {
                logs.add("★ Fiora đạt 4/4 hạ gục — lập tức trừ hết máu Nhà Chính của bạn!")
                s.copy(playerNexusHp = 0)
            }
            s = checkGameOver(s)
        }
        return s
    }

    private fun applyLifesteal(state: CombatState, forPlayer: Boolean, dmg: Int): CombatState {
        if (dmg <= 0) return state
        return if (forPlayer) {
            state.copy(playerNexusHp = minOf(state.playerMaxHp, state.playerNexusHp + dmg))
        } else {
            state.copy(enemyNexusHp = minOf(state.enemyMaxHp, state.enemyNexusHp + dmg))
        }
    }

    internal fun damageUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        damage: Int,
        isSpell: Boolean = false,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String>
    ): CombatState = damageUnitWithResult(state, unitId, isEnemy, damage, isSpell, fromAllyEffect, logs).first

    private fun damageUnitWithResult(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        damage: Int,
        isSpell: Boolean = false,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String>
    ): Pair<CombatState, Int> {
        if (damage <= 0) return state to 0
        val board = if (isEnemy) state.enemyBoard else state.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return state to 0

        if (!isEnemy && target.template.id == "card_guardian_follower" && !target.isSilenced && fromAllyEffect) {
            logs.add("${target.template.name} miễn nhiễm sát thương từ bài phép/Kỹ Năng đồng minh.")
            return state to 0
        }

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải bài phép!")
            val next = if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
            return next to 0
        }

        if (target.hasBarrier) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasBarrier = false) else it
            }
            logs.add("Lá Chắn của ${target.template.name} đã đỡ sát thương!")
            val next = if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
            return next to 0
        }

        val agitatedBonus = if (target.keywords.contains(Keyword.AGITATED)) 1 else 0
        val rawDamage = damage + agitatedBonus
        val afterTough = if (target.keywords.contains(Keyword.TOUGH)) maxOf(0, rawDamage - 1) else rawDamage
        val crystallizeMult = if (isEnemy && target.effectivePower == 0 && state.championId == "champ_anivia" && state.starLevel >= 6) 2 else 1
        val actualDmg = afterTough * crystallizeMult
        if (actualDmg <= 0) return state to 0
        val newHp = target.health - actualDmg

        if (newHp <= 0) {
            return removeUnit(state, unitId, isEnemy, logs) to actualDmg
        } else {
            var newPow = target.power
            var updatedMaxHp = target.maxHealth
            var finalHp = newHp

            if (isEnemy) {
                newPow = PowerResolver.onEnemyDamagedPowerReduction(state, newPow, actualDmg)
            }
            if (!isEnemy && actualDmg > 0 && state.championId == "champ_anivia" && state.starLevel >= 4) {
                newPow += 1
                finalHp += 1
                updatedMaxHp += 1
                logs.add("Bền Bỉ (4★): ${target.template.name} sống sót qua sát thương và nhận +1|+1!")
            }
            var extraGrantedKws = target.keywords
            if (!isEnemy && actualDmg > 0 && target.template.isChampion) {
                val buckleCount = state.equippedRelics.count { it.id == "R0019" }
                if (buckleCount > 0) {
                    val buff = 2 * buckleCount
                    newPow += buff
                    finalHp += buff
                    updatedMaxHp += buff
                    logs.add("The Berserker's Buckle: ${target.template.name} nhận +$buff|+$buff!")
                }
                val plateCount = state.equippedRelics.count { it.id == "R0159" }
                if (plateCount > 0) {
                    val buff = 3 * plateCount
                    newPow += buff
                    finalHp += buff
                    updatedMaxHp += buff
                    extraGrantedKws = extraGrantedKws + Keyword.sharablePool.random()
                    logs.add("Sentinel's Plate: ${target.template.name} nhận +$buff|+$buff và từ khóa mới!")
                }
            }

            val updated = board.map {
                if (it.instanceId == unitId) {
                    it.copy(health = finalHp, maxHealth = updatedMaxHp, power = newPow, keywords = extraGrantedKws)
                } else it
            }
            var s = if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)

            if (actualDmg > 0 && target.template.id == "card_mortal_prophet" && !target.isSilenced) {
                if (!isEnemy) {
                    s = dealDamageToEnemyNexus(s, 1, logs)
                    logs.add("Tiên Tri Chết Chóc sống sót: Gây 1 sát thương lên Nhà Chính địch!")
                } else {
                    s = dealDamageToPlayerNexus(s, 1, logs)
                }
            }
            return s to actualDmg
        }
    }

    private fun cleanCombatUnitReferences(
        state: CombatState,
        unitId: String,
        removeFromBlocksEvenIfCommitted: Boolean = false
    ): CombatState {
        val cleanAttackers = state.attackingUnitIds.filterNot { it == unitId }
        val cleanChallenges = state.challengedPairings.filter { (k, v) -> k != unitId && v != unitId }
        return if (!state.blockCommitted || removeFromBlocksEvenIfCommitted) {
            val cleanPairings = state.blockPairings.filter { (k, v) -> k != unitId && v != unitId }
            val cleanLocked = state.lockedBlockAttackerIds - unitId
            state.copy(
                attackingUnitIds = cleanAttackers,
                challengedPairings = cleanChallenges,
                blockPairings = cleanPairings,
                lockedBlockAttackerIds = cleanLocked
            )
        } else {
            val cleanPairings = state.blockPairings.filter { (k, v) -> k != unitId && v != unitId }
            state.copy(
                attackingUnitIds = cleanAttackers,
                challengedPairings = cleanChallenges,
                blockPairings = cleanPairings
            )
        }
    }

    internal fun releaseCapturedUnits(
        state: CombatState,
        captor: UnitOnBoard,
        logs: MutableList<String>
    ): CombatState {
        if (captor.capturedUnits.isEmpty()) return state
        var s = state
        for (cap in captor.capturedUnits) {
            val restored = cap.copy(
                isStunned = false,
                isFrostbitten = false,
                frostbiteReduction = null,
                hasBarrier = cap.keywords.contains(Keyword.BARRIER)
            )
            if (restored.ownerIsPlayer) {
                if (s.playerBoard.size < 6) {
                    s = s.copy(playerBoard = s.playerBoard + restored)
                    logs.add("${restored.template.name} được giải thoát trở lại Hàng Chờ!")
                }
            } else {
                if (s.enemyBoard.size < 6) {
                    s = s.copy(enemyBoard = s.enemyBoard + restored)
                    logs.add("${restored.template.name} của địch được giải thoát trở lại Hàng Chờ!")
                }
            }
        }
        return s
    }

    internal fun removeUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        val board = if (isEnemy) s.enemyBoard else s.playerBoard
        val idx = board.indexOfFirst { it.instanceId == unitId }
        if (idx < 0) return s
        val target = board[idx]

        val lastBreathRally = !target.isSilenced && target.attachedItems.any { it.onLastBreathRally }

        // Tryndamere Lv1: Xuống <= 0 HP -> Lập tức lên Lv2 (9|9, Áp Đảo, Kiên Cường, Đáng Sợ) thay vì hy sinh!
        if (!isEnemy && target.template.id == "card_tryndamere" && !target.isLeveledUp && !target.isSilenced) {
            val extraPow = maxOf(0, target.power - target.template.basePower)
            val extraHp = maxOf(0, target.maxHealth - target.template.baseHealth)
            val leveledTrynd = target.copy(
                power = 9 + extraPow,
                health = 9 + extraHp,
                maxHealth = 9 + extraHp,
                keywords = (target.keywords - Keyword.EPHEMERAL) + setOf(
                    Keyword.OVERWHELM,
                    Keyword.TOUGH,
                    Keyword.FEARSOME
                ),
                isLeveledUp = true,
                isFrostbitten = false,
                frostbiteReduction = null,
                hasBarrier = false
            )
            s = s.copy(playerBoard = s.playerBoard.map { if (it.instanceId == unitId) leveledTrynd else it })
            if (s.equippedRelics.any { it.levelUpRally }) {
                s = grantPlayerAttackToken(s, logs, "Thăng Cấp Tập Hợp")
            }
            if (s.equippedRelics.any { it.id == "R0031" }) {
                s.enemyBoard.toList().forEach { e -> s = stunEnemyUnit(s, e.instanceId, logs) }
            }
            if (s.equippedRelics.any { it.id == "R0113" }) {
                s = s.copy(playerSpellMana = 3)
            }
            if (s.equippedRelics.any { it.id == "R0105" }) {
                s = s.copy(playerMana = minOf(s.playerMaxMana, s.playerMana + 3))
            }
            s = PowerResolver.onChampionLevelUp(s, logs)
            logs.add("★ Tryndamere xuống 0 Máu — Lập tức Thăng Cấp (${leveledTrynd.power}|${leveledTrynd.health}: Áp Đảo, Kiên Cường, Đáng Sợ)!")
            s = syncChampionSpellsInHand(s)
            return cleanCombatUnitReferences(s, unitId)
        }

        // Deathless revival: counts as death & revive (new instanceId, Hallowed/Last Breath/Graveyard triggered, Stunned, 1 HP)
        if (target.hasDeathless) {
            val withoutOld = board.filterNot { it.instanceId == unitId }
            s = if (isEnemy) {
                s.copy(
                    enemyBoard = withoutOld,
                    enemyGraveyard = s.enemyGraveyard + target.template
                )
            } else {
                s.copy(
                    playerBoard = withoutOld,
                    playerGraveyard = s.playerGraveyard + target.template
                )
            }
            if (target.equippedGear != null) {
                s = returnEquipmentToHand(s, target.equippedGear, toPlayer = !isEnemy, logs = logs)
            }
            if (lastBreathRally) {
                s = if (!isEnemy) grantPlayerAttackToken(s, logs, "Trăn Trối Tập Hợp") else s.copy(enemyHasAttackToken = true)
                logs.add("Trăn Trối Tập Hợp: Nhận Biểu Tượng Tấn Công!")
            }
            s = releaseCapturedUnits(s, target, logs)

            val (unequippedTarget, _) = detachEquipmentFromUnit(target)
            val revivedKeywords = (unequippedTarget.keywords - Keyword.DEATHLESS) + Keyword.STUN
            val revived = unequippedTarget.copy(
                instanceId = if (isEnemy) "eu_${nextId()}" else "u_${nextId()}",
                health = 1,
                hasDeathless = false,
                hasBarrier = revivedKeywords.contains(Keyword.BARRIER),
                hasSpellShield = revivedKeywords.contains(Keyword.SPELLSHIELD),
                isStunned = true,
                isFrostbitten = false,
                frostbiteReduction = null,
                isSilenced = false,
                gloomStacks = 0,
                fatedUsedThisRound = false,
                keywords = revivedKeywords,
                equippedGear = null,
                equippedGearName = null,
                capturedUnits = emptyList()
            )
            val currentBoardAfterRelease = (if (isEnemy) s.enemyBoard else s.playerBoard).toMutableList()
            val insertIdx = minOf(idx, currentBoardAfterRelease.size)
            currentBoardAfterRelease.add(insertIdx, revived)
            s = if (isEnemy) s.copy(enemyBoard = currentBoardAfterRelease) else s.copy(playerBoard = currentBoardAfterRelease)
            logs.add("${target.template.name} hồi sinh nhờ Bất Tử (1 Máu, Choáng)!")
            return cleanCombatUnitReferences(s, unitId)
        }

        // Anivia Last Breath: Transform into Eggnivia (0|2), storing diedAtLevel (1 or 2)
        if (!isEnemy && target.template.id == "card_anivia" && !target.isSilenced) {
            if (target.equippedGear != null) {
                s = returnEquipmentToHand(s, target.equippedGear, toPlayer = true, logs = logs)
            }
            val eggTmpl = CardCatalog.getCard("card_eggnivia")
            val diedLvl = if (target.isLeveledUp) 2 else 1
            val egg = UnitOnBoard(
                instanceId = target.instanceId,
                template = eggTmpl,
                ownerIsPlayer = true,
                power = eggTmpl.basePower,
                health = eggTmpl.baseHealth,
                maxHealth = eggTmpl.baseHealth,
                keywords = eggTmpl.keywords,
                attachedItems = target.attachedItems,
                equippedGear = null,
                equippedGearName = null,
                diedAtLevel = diedLvl
            )
            logs.add("Trăng Trối Anivia (Lv$diedLvl): Biến đổi thành Trứng Anivia (0|2)!")
            s = s.copy(playerBoard = s.playerBoard.map { if (it.instanceId == unitId) egg else it })
            if (lastBreathRally) {
                s = grantPlayerAttackToken(s, logs, "Trăn Trối Tập Hợp")
                logs.add("Trăn Trối Tập Hợp: Nhận Biểu Tượng Tấn Công!")
            }
            s = releaseCapturedUnits(s, target, logs)
            s = syncChampionSpellsInHand(s)
            return cleanCombatUnitReferences(s, unitId)
        }

        val updated = board.filterNot { it.instanceId == unitId }
        s = if (isEnemy) {
            s.copy(
                enemyBoard = updated,
                enemyGraveyard = s.enemyGraveyard + target.template,
                playerSlainCount = s.playerSlainCount + 1,
                playerBoard = s.playerBoard.map { u ->
                    if (u.template.id == "card_nasus" && !u.isSilenced) {
                        u.copy(power = u.power + 1, health = u.health + 1, maxHealth = u.maxHealth + 1)
                    } else u
                }
            )
        } else {
            s.copy(
                playerBoard = updated,
                playerGraveyard = s.playerGraveyard + target.template
            )
        }
        if (target.equippedGear != null) {
            s = returnEquipmentToHand(s, target.equippedGear, toPlayer = !isEnemy, logs = logs)
        }
        s = releaseCapturedUnits(s, target, logs)
        logs.add("${target.template.name} đã bị hạ gục.")

        // Thùng Thuốc Độc (Caustic Cask) Last Breath -> gây 1 sát thương lên cả 2 Nhà Chính
        if (!target.isSilenced && target.template.id == "card_caustic_cask") {
            s = dealDamageToEnemyNexus(s, 1, logs)
            s = dealDamageToPlayerNexus(s, 1, logs)
            logs.add("Thùng Thuốc Độc phát nổ gây 1 sát thương lên cả 2 Nhà Chính!")
        }

        // Lợn Rừng Sát Thủ Last Breath -> Nhận 1 ngọc năng lượng rỗng
        if (!target.isSilenced && target.template.id == "card_ruthless_boar" && !isEnemy) {
            s = gainPlayerManaGem(s, empty = true, logs, "Trăng Trối: ${target.template.name}")
        }

        // Pipeline 4.2: Linh Hồn Lạc Lối Last Breath -> Tạo 1 Song Kiếm Ác Quỷ lên tay
        if (!target.isSilenced && target.template.id == "card_lost_soul") {
            val revenantTmpl = CardCatalog.getCard("card_twinblade_revenant")
            if (!isEnemy && s.playerHand.size < 10) {
                s = s.copy(playerHand = s.playerHand + createHandInstance(revenantTmpl, isCreated = true))
                logs.add("Trăn Trối Linh Hồn Lạc Lối: Tạo Song Kiếm Ác Quỷ lên tay!")
            } else if (isEnemy) {
                s = addEnemyCreatedCardToHand(s, revenantTmpl, logs)
            }
        }

        if (lastBreathRally) {
            s = if (!isEnemy) grantPlayerAttackToken(s, logs, "Trăn Trối Tập Hợp") else s.copy(enemyHasAttackToken = true)
            logs.add("Trăn Trối Tập Hợp: Nhận Biểu Tượng Tấn Công!")
        }

        // Ghi nhận đồng minh hy sinh trong vòng (Speaker, Hoàng Đế Thống Trị, Viego)
        s = recordUnitDeathSideEffects(s, target, isEnemy = isEnemy, logs = logs)

        // Relic & Epic Power on Ally Death
        if (!isEnemy) {
            s = PowerResolver.onAllyDeath(s, target, logs)
            if (s.equippedRelics.any { it.id == "R0166" }) {
                s = dealDamageToEnemyNexus(s, 1, logs)
            }
        }
        // Relic: Voidborne Carapace (When any unit dies -> grant equipped champion its sharable keywords)
        if (s.equippedRelics.any { it.id == "R0049" }) {
            val sharable = target.keywords.filter { it.isSharablePositive }.toSet()
            if (sharable.isNotEmpty()) {
                s = s.copy(playerBoard = s.playerBoard.map { u ->
                    if (u.template.isChampion) u.copy(keywords = u.keywords + sharable) else u
                })
            }
        }

        s = syncChampionSpellsInHand(s)
        return cleanCombatUnitReferences(s, unitId)
    }

    fun recallUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        isSpell: Boolean = true,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        var s = state
        val board = if (isEnemy) s.enemyBoard else s.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return s

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải bài phép!")
            return if (isEnemy) s.copy(enemyBoard = updated) else s.copy(playerBoard = updated)
        }

        val updated = (if (isEnemy) s.enemyBoard else s.playerBoard).filterNot { it.instanceId == unitId }
        s = if (isEnemy) {
            val nextHand = if (s.enemyHand.size < 10) s.enemyHand + target.template else s.enemyHand
            s.copy(
                enemyBoard = updated,
                enemyHand = nextHand
            )
        } else {
            val recalledCard = createHandInstance(target.template, isFleeting = false, isCreated = false)
            val nextHand = if (s.playerHand.size < 10) s.playerHand + recalledCard else s.playerHand
            s.copy(
                playerBoard = updated,
                playerHand = nextHand
            )
        }
        if (target.equippedGear != null) {
            s = returnEquipmentToHand(s, target.equippedGear, toPlayer = !isEnemy, logs = logs)
        }
        s = releaseCapturedUnits(s, target, logs)
        logs.add("${target.template.name} bị Triệu Hồi về tay.")
        s = syncChampionSpellsInHand(s)
        return cleanCombatUnitReferences(s, unitId)
    }

    fun obliterateUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        isSpell: Boolean = true,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        var s = state
        val board = if (isEnemy) s.enemyBoard else s.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return s

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải bài phép!")
            return if (isEnemy) s.copy(enemyBoard = updated) else s.copy(playerBoard = updated)
        }

        val updated = (if (isEnemy) s.enemyBoard else s.playerBoard).filterNot { it.instanceId == unitId }
        s = if (isEnemy) {
            s.copy(enemyBoard = updated)
        } else {
            s.copy(playerBoard = updated)
        }
        if (target.equippedGear != null) {
            s = returnEquipmentToHand(s, target.equippedGear, toPlayer = !isEnemy, logs = logs)
        }
        s = releaseCapturedUnits(s, target, logs)
        logs.add("${target.template.name} bị Hủy Diệt!")
        s = syncChampionSpellsInHand(s)
        return cleanCombatUnitReferences(s, unitId)
    }

    fun captureUnit(
        state: CombatState,
        captorId: String,
        captorIsPlayer: Boolean,
        targetId: String,
        targetIsEnemy: Boolean,
        isSpell: Boolean = true,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        var s = state
        val captorBoard = if (captorIsPlayer) s.playerBoard else s.enemyBoard
        val captor = captorBoard.find { it.instanceId == captorId } ?: return s
        val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
        val target = targetBoard.find { it.instanceId == targetId } ?: return s
        if (captorId == targetId) return s

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = targetBoard.map {
                if (it.instanceId == targetId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải bài phép!")
            return if (targetIsEnemy) s.copy(enemyBoard = updated) else s.copy(playerBoard = updated)
        }

        val (unequippedTarget, gear) = detachEquipmentFromUnit(target)
        val storedCopy = unequippedTarget.copy(
            isStunned = false,
            isFrostbitten = false,
            frostbiteReduction = null,
            hasBarrier = unequippedTarget.keywords.contains(Keyword.BARRIER),
            equippedGear = null,
            equippedGearName = null,
            capturedUnits = emptyList()
        )
        val updatedCaptorBoard = (if (captorIsPlayer) s.playerBoard else s.enemyBoard).map {
            if (it.instanceId == captorId) it.copy(capturedUnits = it.capturedUnits + storedCopy) else it
        }
        s = if (captorIsPlayer) s.copy(playerBoard = updatedCaptorBoard) else s.copy(enemyBoard = updatedCaptorBoard)

        val updatedTargetBoard = (if (targetIsEnemy) s.enemyBoard else s.playerBoard).filterNot { it.instanceId == targetId }
        s = if (targetIsEnemy) s.copy(enemyBoard = updatedTargetBoard) else s.copy(playerBoard = updatedTargetBoard)
        if (gear != null) {
            s = returnEquipmentToHand(s, gear, toPlayer = !targetIsEnemy, logs = logs)
        }
        s = releaseCapturedUnits(s, target, logs)
        logs.add("${captor.template.name} đã Giam Cầm ${target.template.name}!")
        s = syncChampionSpellsInHand(s)
        return cleanCombatUnitReferences(s, targetId)
    }

    fun silenceUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        isSpell: Boolean = true,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        var s = state
        val board = if (isEnemy) s.enemyBoard else s.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return s

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải Câm Lặng!")
            return if (isEnemy) s.copy(enemyBoard = updated) else s.copy(playerBoard = updated)
        }

        val basePow = target.template.basePower
        val baseMaxHp = target.template.baseHealth
        val keptHp = maxOf(1, minOf(target.health, baseMaxHp))
        val silenced = target.copy(
            power = basePow,
            health = keptHp,
            maxHealth = baseMaxHp,
            keywords = setOf(Keyword.SILENCE),
            impactStacks = 0,
            spiritStacks = 0,
            gloomStacks = 0,
            hasBarrier = false,
            hasSpellShield = false,
            isStunned = false,
            isFrostbitten = false,
            frostbiteReduction = null,
            isSilenced = true,
            isShackled = false,
            hasDeathless = false,
            hasEnlightenedBonusApplied = false,
            hasDeepBonusApplied = false,
            hasEvolveBonusApplied = false,
            supportPowerBonus = 0,
            supportHealthBonus = 0,
            supportKeyword = null,
            equippedGear = null,
            equippedGearName = null,
            capturedUnits = emptyList(),
            killCount = 0
        )
        val updatedBoard = board.map { if (it.instanceId == unitId) silenced else it }
        s = if (isEnemy) {
            s.copy(enemyBoard = updatedBoard)
        } else {
            s.copy(
                playerBoard = updatedBoard,
                roundSupportPowerBuffs = s.roundSupportPowerBuffs - unitId,
                roundSupportHealthBuffs = s.roundSupportHealthBuffs - unitId,
                roundSupportKeywords = s.roundSupportKeywords - unitId
            )
        }
        s = releaseCapturedUnits(s, target, logs)
        logs.add("${target.template.name} bị Câm Lặng!")
        return s
    }

    fun applySpirit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        stacks: Int = 1,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        if (stacks <= 0) return state
        val board = if (isEnemy) state.enemyBoard else state.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return state
        val updated = board.map { u ->
            if (u.instanceId == unitId) {
                u.copy(
                    power = u.power + stacks,
                    health = u.health + stacks,
                    maxHealth = u.maxHealth + stacks,
                    spiritStacks = u.spiritStacks + stacks,
                    keywords = u.keywords + Keyword.SPIRIT
                )
            } else u
        }
        logs.add("${target.template.name} nhận Tinh Linh +$stacks|+$stacks.")
        val updatedState = if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
        return if (!isEnemy) refreshEvolveAndSeenKeywords(updatedState, logs) else updatedState
    }

    fun applyGloom(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        stacks: Int = 1,
        isSpell: Boolean = false,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        if (stacks <= 0) return state
        val board = if (isEnemy) state.enemyBoard else state.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return state

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải U Ám!")
            return if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
        }

        val newHp = target.health - stacks
        val newMaxHp = maxOf(0, target.maxHealth - stacks)
        val updated = board.map { u ->
            if (u.instanceId == unitId) {
                u.copy(
                    health = newHp,
                    maxHealth = newMaxHp,
                    gloomStacks = u.gloomStacks + stacks,
                    keywords = u.keywords + Keyword.GLOOM
                )
            } else u
        }
        var s = if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
        logs.add("${target.template.name} chịu U Ám -$stacks|-$stacks.")
        if (newHp <= 0) {
            s = removeUnit(s, unitId, isEnemy = isEnemy, logs = logs)
        }
        return s
    }

    internal fun frostbiteUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        isSpell: Boolean = true,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        val board = if (isEnemy) state.enemyBoard else state.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return state

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải Đóng Băng!")
            return if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
        }

        val wasFrostbitten = target.isFrostbitten
        val dropAmount = target.effectivePower
        val nextReduction = (target.frostbiteReduction ?: 0) + dropAmount
        val updated = board.map {
            if (it.instanceId == unitId) {
                it.copy(isFrostbitten = true, frostbiteReduction = nextReduction)
            } else it
        }
        logs.add("Đã Đóng Băng ${target.template.name}.")
        return if (isEnemy) {
            state.copy(
                enemyBoard = updated,
                enemiesFrostbittenCount = state.enemiesFrostbittenCount + (if (!wasFrostbitten) 1 else 0)
            )
        } else {
            state.copy(playerBoard = updated)
        }
    }

    internal fun stunUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        isSpell: Boolean = false,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        var s = state
        val board = if (isEnemy) s.enemyBoard else s.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return s

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải bài phép!")
            return if (isEnemy) s.copy(enemyBoard = updated) else s.copy(playerBoard = updated)
        }

        val updatedBoard = board.map {
            if (it.instanceId == unitId) it.copy(isStunned = true, keywords = it.keywords + Keyword.STUN) else it
        }
        s = if (isEnemy) {
            s.copy(
                enemyBoard = updatedBoard,
                enemiesStunnedCount = s.enemiesStunnedCount + 1
            )
        } else {
            s.copy(playerBoard = updatedBoard)
        }
        s = cleanCombatUnitReferences(s, unitId)
        logs.add("Đã làm Choáng ${target.template.name}.")

        if (isEnemy) {
            val yasuos = s.playerBoard.filter { it.template.id == "card_yasuo" && !it.isSilenced }
            yasuos.forEach { yasuo ->
                val baseDmg = if (yasuo.isLeveledUp) yasuo.effectivePower else 2
                val dmg = calculateFinalDamage(baseDmg, yasuo.template, s, sourceIsPlayer = true)
                s = damageUnit(s, unitId, isEnemy = true, damage = dmg, logs = logs)
                logs.add("Yasuo chém ${target.template.name} đang Choáng gây $dmg sát thương!")
            }
            if (s.championId == "champ_yasuo" && s.starLevel >= 6) {
                val star6Dmg = calculateFinalDamage(5, null, s, sourceIsPlayer = true)
                s = damageUnit(s, unitId, isEnemy = true, damage = star6Dmg, logs = logs)
            }
            s = s.copy(playerBoard = s.playerBoard.map {
                if (it.template.id == "card_fae_blader" && !it.isSilenced) it.copy(power = it.power + 2) else it
            })
        }
        return s
    }

    internal fun stunEnemyUnit(state: CombatState, unitId: String, logs: MutableList<String>): CombatState {
        return stunUnit(state, unitId, isEnemy = true, isSpell = false, fromAllyEffect = false, logs = logs)
    }

    internal fun dealDamageToEnemyNexus(state: CombatState, dmg: Int, logs: MutableList<String>): CombatState {
        if (dmg <= 0) return state
        var s = state.copy(
            enemyNexusHp = maxOf(0, state.enemyNexusHp - dmg),
            playerPlunderActive = true
        )
        s = PowerResolver.onEnemyNexusDamaged(s)
        return checkGameOver(s)
    }

    internal fun dealDamageToPlayerNexus(state: CombatState, dmg: Int, logs: MutableList<String>): CombatState {
        if (dmg <= 0) return state
        val hasNexusTough = state.equippedRelics.any { it.nexusTough } || PowerResolver.hasNexusToughPower(state)
        val effectiveDmg = if (hasNexusTough) maxOf(0, dmg - 1) else dmg
        if (effectiveDmg <= 0) return state
        val s = state.copy(playerNexusHp = maxOf(0, state.playerNexusHp - effectiveDmg))
        return checkGameOver(s)
    }

    // --- PRIORITY PASS & ROUND TRANSITION ---

    fun passPriority(state: CombatState): CombatState {
        if (state.phase == CombatPhase.GAME_OVER || state.priorityHolder != PriorityHolder.PLAYER) return state
        val logs = state.combatLog.toMutableList()

        // Rule 9.2: Passing during DECLARE_BLOCK with 0+ blockers commits blocks
        if (state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE) {
            return commitPlayerBlock(state)
        }

        // Rule 9.2: Passing while Spell Stack is non-empty resolves stack LIFO and transfers priority
        if (state.spellStack.isNotEmpty()) {
            logs.add("Bạn đã bỏ qua — đang thực thi hàng chờ phép.")
            return resolveSpellStack(state.copy(combatLog = logs.takeLast(16)))
        }

        // Rule 9.3: Passing in MAIN_ACTION_PHASE increments consecutivePasses; 2 passes ends round
        val newPasses = state.consecutivePasses + 1
        if (newPasses >= 2) {
            logs.add("Cả hai bên đều bỏ qua. Kết thúc Vòng ${state.round}.")
            return advanceToNextRound(state, logs)
        }

        logs.add("Bạn đã chuyển lượt.")
        return state.copy(
            consecutivePasses = newPasses,
            priorityHolder = PriorityHolder.ENEMY,
            combatLog = logs.takeLast(16)
        )
    }

    internal fun drawEnemyCard(state: CombatState): CombatState {
        if (state.enemyDrawPool.isEmpty()) {
            val logs = state.combatLog.toMutableList()
            logs.add("Bộ bài địch đã hết! Kiệt sức gây $EMPTY_DECK_FATIGUE_DAMAGE sát thương lên Nhà Chính địch.")
            val afterFatigue = dealDamageToEnemyNexus(state, EMPTY_DECK_FATIGUE_DAMAGE, logs)
            return afterFatigue.copy(combatLog = logs.takeLast(16))
        }
        val drawn = state.enemyDrawPool.random()
        var s = state
        val logs = s.combatLog.toMutableList()
        if (s.enemyDeckTrapsCount > 0) {
            s = triggerPlayerTrapOnEnemyDraw(s, drawn, logs)
        }
        return if (s.enemyHand.size >= 10) {
            s.copy(combatLog = logs.takeLast(16))
        } else {
            s.copy(enemyHand = s.enemyHand + drawn, combatLog = logs.takeLast(16))
        }
    }

    internal fun summonEnemyUnitFromPool(
        state: CombatState,
        chosen: CardTemplate,
        spendMana: Int,
        logs: MutableList<String>,
        forceEphemeral: Boolean = false,
        sacrificeTargetId: String? = null
    ): CombatState {
        var s = state
        if (chosen.requiresAllySacrificeOnPlay) {
            val victim = sacrificeTargetId?.let { id ->
                s.enemyBoard.find { it.instanceId == id && it.template.type != CardType.LANDMARK }
            } ?: s.enemyBoard
                .filter { it.template.type != CardType.LANDMARK && !it.template.isChampion }
                .minByOrNull { it.effectivePower + it.health }
                ?: s.enemyBoard
                    .filter { it.template.type != CardType.LANDMARK }
                    .minByOrNull { it.effectivePower + it.health }
            if (victim != null) {
                s = removeUnit(s, victim.instanceId, isEnemy = true, logs = logs)
                logs.add("Địch hy sinh ${victim.template.name} để triệu hồi ${chosen.name}!")
            }
        }
        if (s.enemyBoard.size >= 6) return s

        val isDeadlyActive = s.mutators.any { it.id == "mut_deadly" }
        var bonusPow = if (chosen.keywords.contains(Keyword.LURK)) s.enemyLurkBonus else 0
        var bonusHp = 0
        val extraKw = mutableSetOf<Keyword>()
        if (forceEphemeral) extraKw.add(Keyword.EPHEMERAL)
        if (!isDeadlyActive && s.adventure.starRating >= 3.0f) {
            bonusPow += 1; bonusHp += 1
        }
        if (chosen.id == "card_encroaching_mist") {
            bonusPow += s.enemyMistStatBonus
            bonusHp += s.enemyMistStatBonus
        } else if (chosen.id == "card_viego") {
            bonusPow += s.enemyViegoStatBonus
            bonusHp += s.enemyViegoStatBonus
        }
        when (canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle)) {
            "VOLATILE_MUNITIONS" -> extraKw.add(Keyword.IMPACT)
            "UNSEEN_PATH" -> if (chosen.keywords.contains(Keyword.ELUSIVE)) bonusPow += 1
            "GLORIOUS_EVOLUTION" -> {
                bonusPow += 1; bonusHp += 1
                val availableKw = Keyword.sharablePool.filterNot { it in chosen.keywords }
                extraKw.add((availableKw.ifEmpty { Keyword.sharablePool }).random())
            }
            "BLAZING_DAWN" -> {
                bonusPow += 1; bonusHp += 1
            }
        }

        var finalPow = chosen.basePower + bonusPow
        var finalHp = chosen.baseHealth + bonusHp
        if (isDeadlyActive && chosen.type != CardType.LANDMARK) {
            val deadlyPow = (finalPow + 1) / 2
            val deadlyHp = (finalHp + 1) / 2
            finalPow += deadlyPow
            finalHp += deadlyHp
        }

        val enemyUnit = UnitOnBoard(
            instanceId = "eu_${nextId()}",
            template = chosen,
            ownerIsPlayer = false,
            power = finalPow,
            health = finalHp,
            maxHealth = finalHp,
            keywords = chosen.keywords + extraKw,
            impactStacks = chosen.impactStacks + (if (extraKw.contains(Keyword.IMPACT)) 1 else 0),
            hasBarrier = chosen.keywords.contains(Keyword.BARRIER),
            hasSpellShield = chosen.keywords.contains(Keyword.SPELLSHIELD),
            hasDeathless = chosen.keywords.contains(Keyword.DEATHLESS)
        )
        logs.add("Địch triệu hồi ${chosen.name} (${enemyUnit.power}|${enemyUnit.health}).")
        s = s.copy(
            enemyMana = maxOf(0, s.enemyMana - spendMana),
            enemyHand = removeFirstTemplate(s.enemyHand, chosen.id),
            enemyBoard = s.enemyBoard + enemyUnit
        )

        // Đại Bàng Trinh Sát đang ở trên sân nhận Thách Đấu khi đồng minh khác được triệu hồi
        s = grantFleetfeatherChallengerOnSummon(s, newlySummonedId = enemyUnit.instanceId, forPlayer = false)

        // Hiệu ứng khi triệu hồi
        when (chosen.id) {
            "card_laurent_bladekeeper" -> {
                val ally = s.enemyBoard
                    .filter { it.instanceId != enemyUnit.instanceId && it.template.type != CardType.LANDMARK }
                    .maxByOrNull { it.effectivePower }
                if (ally != null) {
                    s = s.copy(enemyBoard = s.enemyBoard.map { u ->
                        if (u.instanceId == ally.instanceId) {
                            u.copy(power = u.power + 2, health = u.health + 2, maxHealth = u.maxHealth + 2)
                        } else u
                    })
                    logs.add("Người Giữ Gươm Laurent ban +2|+2 cho ${ally.template.name}!")
                }
            }
            "card_tiana_crownguard" -> {
                s = s.copy(enemyBoard = s.enemyBoard.map { u ->
                    u.copy(keywords = u.keywords + Keyword.TOUGH)
                })
                logs.add("Tiana ban Kiên Cường cho cả đội địch!")
            }
            "card_fading_icon" -> {
                if (s.enemyBoard.size < 6) {
                    s = summonEnemyUnitFromPool(s, CardCatalog.getCard("card_spiderling"), spendMana = 0, logs = logs)
                }
            }
            "card_camavoran_soldier", "card_invasive_hydravine" -> {
                if (s.enemyBoard.size < 6) {
                    s = summonEnemyUnitFromPool(s, CardCatalog.getCard("card_encroaching_mist"), spendMana = 0, logs = logs)
                }
            }
            "card_encroaching_mist" -> {
                s = applyEncroachingMistSummonBuff(s, forPlayer = false, logs = logs)
            }
        }

        // Tryndamere 4★ Địa Hình Hiểm Trở: Lần đầu tiên địch triệu hồi bài quân mỗi vòng, Đóng Băng bài quân đó
        if (s.championId == "champ_tryndamere" &&
            s.starLevel >= 4 &&
            chosen.type != CardType.LANDMARK &&
            !s.enemySummonedThisRound &&
            s.enemyBoard.any { it.instanceId == enemyUnit.instanceId }
        ) {
            s = s.copy(enemySummonedThisRound = true)
            s = frostbiteUnit(s, enemyUnit.instanceId, isEnemy = true, isSpell = false, fromAllyEffect = false, logs = logs)
            logs.add("Địa Hình Hiểm Trở (Tryndamere 4★): Đóng Băng ${chosen.name}!")
        }
        return s
    }

    private fun grantFleetfeatherChallengerOnSummon(
        state: CombatState,
        newlySummonedId: String,
        forPlayer: Boolean
    ): CombatState {
        val board = if (forPlayer) state.playerBoard else state.enemyBoard
        var changed = false
        val updated = board.map { u ->
            if (u.instanceId != newlySummonedId &&
                u.template.id == "card_fleetfeather_tracker" &&
                !u.isSilenced &&
                !u.keywords.contains(Keyword.CHALLENGER)
            ) {
                changed = true
                u.copy(keywords = u.keywords + Keyword.CHALLENGER)
            } else u
        }
        if (!changed) return state
        return if (forPlayer) state.copy(playerBoard = updated) else state.copy(enemyBoard = updated)
    }

    internal fun applyEncroachingMistSummonBuff(
        state: CombatState,
        forPlayer: Boolean,
        logs: MutableList<String>
    ): CombatState {
        return if (forPlayer) {
            val nextMistBonus = state.playerMistStatBonus + 1
            val nextViegoBonus = state.playerViegoStatBonus + 1
            val updated = state.playerBoard.map { u ->
                if (u.template.id == "card_encroaching_mist" || u.template.id == "card_viego") {
                    u.copy(power = u.power + 1, health = u.health + 1, maxHealth = u.maxHealth + 1)
                } else u
            }
            logs.add("Màn Sương Xâm Chiếm ban +1|+1 cho Viego và mọi Màn Sương Xâm Chiếm ở mọi nơi!")
            state.copy(
                playerMistStatBonus = nextMistBonus,
                playerViegoStatBonus = nextViegoBonus,
                playerBoard = updated
            )
        } else {
            val nextMistBonus = state.enemyMistStatBonus + 1
            val nextViegoBonus = state.enemyViegoStatBonus + 1
            val updated = state.enemyBoard.map { u ->
                if (u.template.id == "card_encroaching_mist" || u.template.id == "card_viego") {
                    u.copy(power = u.power + 1, health = u.health + 1, maxHealth = u.maxHealth + 1)
                } else u
            }
            logs.add("Màn Sương Xâm Chiếm ban +1|+1 cho Viego và mọi Màn Sương Xâm Chiếm của địch ở mọi nơi!")
            state.copy(
                enemyMistStatBonus = nextMistBonus,
                enemyViegoStatBonus = nextViegoBonus,
                enemyBoard = updated
            )
        }
    }

    private fun recordUnitDeathSideEffects(
        state: CombatState,
        deadUnit: UnitOnBoard,
        isEnemy: Boolean,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        s = if (isEnemy) {
            s.copy(
                enemyAllyDiedThisRound = true,
                tempPossessedReturns = s.tempPossessedReturns - deadUnit.instanceId
            )
        } else {
            val followers = if (!deadUnit.template.isChampion && deadUnit.template.type != CardType.LANDMARK) {
                s.playerFollowersDiedThisRound + deadUnit
            } else {
                s.playerFollowersDiedThisRound
            }
            s.copy(
                playerAllyDiedThisRound = true,
                playerFollowersDiedThisRound = followers,
                tempPossessedReturns = s.tempPossessedReturns - deadUnit.instanceId
            )
        }

        // Quý Tộc (Last Breath): Triệu hồi 1 Nhện (1/1)
        if (!deadUnit.isSilenced && deadUnit.template.id == "card_hapless_aristocrat") {
            val spiderTmpl = CardCatalog.getCard("card_spiderling")
            if (isEnemy && s.enemyBoard.size < 6) {
                s = summonEnemyUnitFromPool(s, spiderTmpl, spendMana = 0, logs = logs)
            } else if (!isEnemy && s.playerBoard.size < 6) {
                s = summonPlayerUnit(s, createHandInstance(spiderTmpl), logs).first
            }
        }

        // Viego theo dõi đồng minh hy sinh (20+ Sức Mạnh -> Thăng Cấp Lv2; lần đầu mỗi vòng triệu hồi Màn Sương Xâm Chiếm)
        val deadPower = maxOf(0, deadUnit.effectivePower)
        if (isEnemy) {
            val hasActiveViego = s.enemyBoard.any { it.template.id == "card_viego" && !it.isSilenced && it.instanceId != deadUnit.instanceId }
            if (hasActiveViego) {
                val shouldSummonMist = !s.enemyOtherAllyDiedForViegoThisRound && s.enemyBoard.size < 6
                val updatedEnemy = s.enemyBoard.map { u ->
                    if (u.template.id == "card_viego" && !u.isSilenced) {
                        val nextProg = u.levelProgress + deadPower
                        if (!u.isLeveledUp && nextProg >= 20) {
                            logs.add("★ Viego ĐÃ THĂNG CẤP Lv2! (+${u.template.levelUpPowerBonus}|+${u.template.levelUpHealthBonus})")
                            u.copy(
                                levelProgress = nextProg,
                                isLeveledUp = true,
                                power = u.power + u.template.levelUpPowerBonus,
                                health = u.health + u.template.levelUpHealthBonus,
                                maxHealth = u.maxHealth + u.template.levelUpHealthBonus
                            )
                        } else {
                            u.copy(levelProgress = nextProg)
                        }
                    } else u
                }
                s = s.copy(
                    enemyBoard = updatedEnemy,
                    enemyOtherAllyDiedForViegoThisRound = s.enemyOtherAllyDiedForViegoThisRound || shouldSummonMist
                )
                if (shouldSummonMist) {
                    s = summonEnemyUnitFromPool(s, CardCatalog.getCard("card_encroaching_mist"), spendMana = 0, logs = logs)
                }
            }
        } else {
            val hasActiveViego = s.playerBoard.any { it.template.id == "card_viego" && !it.isSilenced && it.instanceId != deadUnit.instanceId }
            if (hasActiveViego) {
                val shouldSummonMist = !s.playerOtherAllyDiedForViegoThisRound && s.playerBoard.size < 6
                val updatedPlayer = s.playerBoard.map { u ->
                    if (u.template.id == "card_viego" && !u.isSilenced) {
                        val nextProg = u.levelProgress + deadPower
                        if (!u.isLeveledUp && nextProg >= 20) {
                            logs.add("★ Viego của bạn ĐÃ THĂNG CẤP Lv2! (+${u.template.levelUpPowerBonus}|+${u.template.levelUpHealthBonus})")
                            u.copy(
                                levelProgress = nextProg,
                                isLeveledUp = true,
                                power = u.power + u.template.levelUpPowerBonus,
                                health = u.health + u.template.levelUpHealthBonus,
                                maxHealth = u.maxHealth + u.template.levelUpHealthBonus
                            )
                        } else {
                            u.copy(levelProgress = nextProg)
                        }
                    } else u
                }
                s = s.copy(
                    playerBoard = updatedPlayer,
                    playerOtherAllyDiedForViegoThisRound = s.playerOtherAllyDiedForViegoThisRound || shouldSummonMist
                )
                if (shouldSummonMist) {
                    s = summonPlayerUnit(s, createHandInstance(CardCatalog.getCard("card_encroaching_mist")), logs).first
                }
            }
        }
        return s
    }

    internal fun grantEnemyChallengerShowdownBuff(
        state: CombatState,
        attackerId: String,
        logs: MutableList<String>
    ): CombatState {
        if (canonicalEnemyPowerKey(state.encounterNode.enemyPowerTitle) != "FIORA_SHOWDOWN") return state
        val atk = state.enemyBoard.find { it.instanceId == attackerId } ?: return state
        val addedTough = if (atk.keywords.contains(Keyword.TOUGH)) emptySet() else setOf(Keyword.TOUGH)
        val newTempPow = state.roundTempPowerBuffs + (attackerId to ((state.roundTempPowerBuffs[attackerId] ?: 0) + 2))
        val newTempHp = state.roundTempHealthBuffs + (attackerId to ((state.roundTempHealthBuffs[attackerId] ?: 0) + 2))
        val existingTempKw = state.roundTempKeywords[attackerId] ?: emptySet()
        val newTempKw = state.roundTempKeywords + (attackerId to (existingTempKw + addedTough))
        logs.add("Thời Khắc Đối Đầu: ${atk.template.name} nhận +2|+2 và Kiên Cường trong vòng này!")
        return state.copy(
            enemyBoard = state.enemyBoard.map { u ->
                if (u.instanceId == attackerId) {
                    u.copy(
                        power = u.power + 2,
                        health = u.health + 2,
                        maxHealth = u.maxHealth + 2,
                        keywords = u.keywords + Keyword.TOUGH
                    )
                } else u
            },
            roundTempPowerBuffs = newTempPow,
            roundTempHealthBuffs = newTempHp,
            roundTempKeywords = newTempKw
        )
    }

    internal fun removeFirstTemplate(hand: List<CardTemplate>, cardId: String): List<CardTemplate> {
        val idx = hand.indexOfFirst { it.id == cardId }
        if (idx < 0) return hand
        return hand.toMutableList().apply { removeAt(idx) }
    }

    internal fun advanceToNextRound(state: CombatState, logs: MutableList<String>): CombatState {
        var s = state

        // Ephemeral units die at Round End via removeUnit (triggering Last Breath, Deathless, Captured release)
        val ephemeralPlayerIds = s.playerBoard.filter { it.keywords.contains(Keyword.EPHEMERAL) }.map { it.instanceId }
        ephemeralPlayerIds.forEach { id ->
            s = removeUnit(s, id, isEnemy = false, logs = logs)
        }
        val ephemeralEnemyIds = s.enemyBoard.filter { it.keywords.contains(Keyword.EPHEMERAL) }.map { it.instanceId }
        ephemeralEnemyIds.forEach { id ->
            s = removeUnit(s, id, isEnemy = true, logs = logs)
        }

        // Speaker (Kết Thúc Vòng Đấu): Nếu có đồng minh hy sinh trong vòng, ban +1|+1 cho đồng minh mạnh nhất
        if (s.enemyAllyDiedThisRound && s.enemyBoard.any { it.template.id == "card_dawnspeakers" && !it.isSilenced }) {
            val speakerCount = s.enemyBoard.count { it.template.id == "card_dawnspeakers" && !it.isSilenced }
            s.enemyBoard.maxWithOrNull(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health })?.let { topAlly ->
                s = s.copy(enemyBoard = s.enemyBoard.map { u ->
                    if (u.instanceId == topAlly.instanceId) {
                        u.copy(
                            power = u.power + speakerCount,
                            health = u.health + speakerCount,
                            maxHealth = u.maxHealth + speakerCount
                        )
                    } else u
                })
                logs.add("Speaker ban +$speakerCount|+$speakerCount cho ${topAlly.template.name}!")
            }
        }
        if (s.playerAllyDiedThisRound && s.playerBoard.any { it.template.id == "card_dawnspeakers" && !it.isSilenced }) {
            val speakerCount = s.playerBoard.count { it.template.id == "card_dawnspeakers" && !it.isSilenced }
            s.playerBoard.maxWithOrNull(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health })?.let { topAlly ->
                s = s.copy(playerBoard = s.playerBoard.map { u ->
                    if (u.instanceId == topAlly.instanceId) {
                        u.copy(
                            power = u.power + speakerCount,
                            health = u.health + speakerCount,
                            maxHealth = u.maxHealth + speakerCount
                        )
                    } else u
                })
                logs.add("Speaker của bạn ban +$speakerCount|+$speakerCount cho ${topAlly.template.name}!")
            }
        }

        // Hoàn trả bài quân bị Chiếm Hữu (trong vòng này) về chủ sở hữu gốc
        if (s.tempPossessedReturns.isNotEmpty()) {
            val nextPlayer = s.playerBoard.toMutableList()
            val nextEnemy = s.enemyBoard.toMutableList()
            s.tempPossessedReturns.forEach { (unitId, origIsPlayer) ->
                if (origIsPlayer) {
                    val idx = nextEnemy.indexOfFirst { it.instanceId == unitId }
                    if (idx >= 0 && nextPlayer.size < 6) {
                        val unit = nextEnemy.removeAt(idx).copy(ownerIsPlayer = true)
                        nextPlayer.add(unit)
                        logs.add("${unit.template.name} trở về hàng chờ của bạn sau khi hết Chiếm Hữu.")
                    }
                } else {
                    val idx = nextPlayer.indexOfFirst { it.instanceId == unitId }
                    if (idx >= 0 && nextEnemy.size < 6) {
                        val unit = nextPlayer.removeAt(idx).copy(ownerIsPlayer = false)
                        nextEnemy.add(unit)
                        logs.add("${unit.template.name} trở về hàng chờ của địch sau khi hết Chiếm Hữu.")
                    }
                }
            }
            s = s.copy(playerBoard = nextPlayer, enemyBoard = nextEnemy)
        }

        s = PowerResolver.onRoundEndDamageAndKills(s, logs)

        // Enemy Karma Round End spell creation (interceptable by P0756)
        if (s.enemyBoard.any { it.template.id == "card_karma" && !it.isSilenced }) {
            val spellChoices = CardCatalog.cards.values.filter { it.type == CardType.SPELL && it.isCollectibleReward }
            if (spellChoices.isNotEmpty()) {
                s = addEnemyCreatedCardToHand(s, spellChoices.random(), logs)
            }
        }

        // P0093: Không Thể Ngừng Lại - Các buff chỉ số & từ khóa "trong vòng này" của đồng minh (trừ Lá Chắn) tồn tại vĩnh viễn
        val keepPlayerRoundBuffs = PowerResolver.shouldKeepRoundBuffs(s)
        val expiredPlayerTempIds = mutableListOf<String>()
        val cleanPlayerBoard = s.playerBoard.map { u ->
            val healedHp = if (u.keywords.contains(Keyword.REGENERATION)) u.maxHealth else u.health
            val supPow = if (keepPlayerRoundBuffs) 0 else (s.roundSupportPowerBuffs[u.instanceId] ?: 0) + (s.roundTempPowerBuffs[u.instanceId] ?: 0)
            val supHp = if (keepPlayerRoundBuffs) 0 else (s.roundSupportHealthBuffs[u.instanceId] ?: 0) + (s.roundTempHealthBuffs[u.instanceId] ?: 0)
            val roundKwsForUnit = (s.roundSupportKeywords[u.instanceId] ?: emptySet()) + (s.roundTempKeywords[u.instanceId] ?: emptySet())
            val supKws = if (keepPlayerRoundBuffs) {
                roundKwsForUnit.filterTo(mutableSetOf()) { it == Keyword.BARRIER }
            } else {
                roundKwsForUnit
            }
            val newMaxHp = maxOf(1, u.maxHealth - supHp)
            val rawHp = healedHp - supHp
            if (u.template.type != CardType.LANDMARK && supHp > 0 && rawHp <= 0) {
                expiredPlayerTempIds += u.instanceId
            }
            val newHp = if (u.template.type != CardType.LANDMARK && supHp > 0) {
                minOf(newMaxHp, maxOf(0, rawHp))
            } else {
                minOf(newMaxHp, maxOf(1, rawHp))
            }
            u.copy(
                power = maxOf(0, u.power - supPow),
                health = newHp,
                maxHealth = newMaxHp,
                hasBarrier = false,
                isStunned = false,
                isFrostbitten = false,
                frostbiteReduction = null,
                fatedUsedThisRound = false,
                keywords = (u.keywords - Keyword.STUN) - supKws
            )
        }
        val expiredEnemyTempIds = mutableListOf<String>()
        val cleanEnemyBoard = s.enemyBoard.map { u ->
            val healedHp = if (u.keywords.contains(Keyword.REGENERATION)) u.maxHealth else u.health
            val supPow = (s.roundSupportPowerBuffs[u.instanceId] ?: 0) + (s.roundTempPowerBuffs[u.instanceId] ?: 0)
            val supHp = (s.roundSupportHealthBuffs[u.instanceId] ?: 0) + (s.roundTempHealthBuffs[u.instanceId] ?: 0)
            val supKws = (s.roundSupportKeywords[u.instanceId] ?: emptySet()) + (s.roundTempKeywords[u.instanceId] ?: emptySet())
            val newMaxHp = maxOf(1, u.maxHealth - supHp)
            val rawHp = healedHp - supHp
            if (u.template.type != CardType.LANDMARK && supHp > 0 && rawHp <= 0) {
                expiredEnemyTempIds += u.instanceId
            }
            val newHp = if (u.template.type != CardType.LANDMARK && supHp > 0) {
                minOf(newMaxHp, maxOf(0, rawHp))
            } else {
                minOf(newMaxHp, maxOf(1, rawHp))
            }
            u.copy(
                power = maxOf(0, u.power - supPow),
                health = newHp,
                maxHealth = newMaxHp,
                hasBarrier = false,
                isStunned = false,
                isFrostbitten = false,
                frostbiteReduction = null,
                fatedUsedThisRound = false,
                keywords = (u.keywords - Keyword.STUN) - supKws
            )
        }
        s = s.copy(playerBoard = cleanPlayerBoard, enemyBoard = cleanEnemyBoard)
        expiredPlayerTempIds.forEach { id ->
            s = removeUnit(s, id, isEnemy = false, logs = logs)
        }
        expiredEnemyTempIds.forEach { id ->
            s = removeUnit(s, id, isEnemy = true, logs = logs)
        }

        val strongestDeadFollower = s.playerFollowersDiedThisRound.maxWithOrNull(
            compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health }.thenBy { it.template.cost }
        )

        var cleanHand = s.playerHand.filterNot { s.effectiveIsFleeting(it) }

        val nextRound = s.round + 1
        val prevPlayerMax = s.playerMaxMana.coerceIn(1, 10)
        val prevPermanentPlayerMax = (prevPlayerMax - s.playerTempManaFromEmptyGems).coerceIn(1, 10)
        val nextPermanentPlayerMax = (prevPermanentPlayerMax + 1).coerceIn(1, 10)
        val newPlayerMax = minOf(10, nextPermanentPlayerMax + s.pendingEmptyManaGems)
        val nextPlayerTempFromEmpty = (newPlayerMax - nextPermanentPlayerMax).coerceAtLeast(0)

        val prevEnemyMax = s.enemyMaxMana.coerceIn(1, 10)
        val newEnemyMax = (prevEnemyMax + 1).coerceIn(1, 10)
        val unusedPlayerRegular = s.playerMana.coerceIn(0, prevPlayerMax)
        val unusedEnemyRegular = s.enemyMana.coerceIn(0, prevEnemyMax)
        val playerSpellBank = minOf(3, s.playerSpellMana.coerceIn(0, 3) + unusedPlayerRegular)
        val enemySpellBank = minOf(3, s.enemySpellMana.coerceIn(0, 3) + unusedEnemyRegular)

        val playerGetsToken = nextRound % 2 == 1
        val roundInitiator = if (playerGetsToken) PriorityHolder.PLAYER else PriorityHolder.ENEMY
        logs.add("--- Vòng $nextRound (Lượt Tấn Công: ${if (playerGetsToken) "Bạn" else "Địch"}) ---")
        if (nextPlayerTempFromEmpty > 0) {
            logs.add("Mana rỗng ban +$nextPlayerTempFromEmpty Mana tạm thời cho Vòng $nextRound (Tổng: $newPlayerMax).")
        }

        s = s.copy(
            round = nextRound,
            playerMana = newPlayerMax,
            playerMaxMana = newPlayerMax,
            playerTempManaFromEmptyGems = nextPlayerTempFromEmpty,
            pendingEmptyManaGems = 0,
            playerSpellMana = playerSpellBank,
            enemyMana = newEnemyMax,
            enemyMaxMana = newEnemyMax,
            enemySpellMana = enemySpellBank,
            playerHasAttackToken = false,
            enemyHasAttackToken = !playerGetsToken,
            scoutUsedThisRound = false,
            pendingScoutRally = false,
            playerLurkTriggeredThisRound = false,
            enemyLurkTriggeredThisRound = false,
            roundSupportPowerBuffs = emptyMap(),
            roundSupportHealthBuffs = emptyMap(),
            roundSupportKeywords = emptyMap(),
            roundTempPowerBuffs = emptyMap(),
            roundTempHealthBuffs = emptyMap(),
            roundTempKeywords = emptyMap(),
            tempPossessedReturns = emptyMap(),
            enemyAllyDiedThisRound = false,
            playerAllyDiedThisRound = false,
            enemyOtherAllyDiedForViegoThisRound = false,
            playerOtherAllyDiedForViegoThisRound = false,
            playerFollowersDiedThisRound = emptyList(),
            playerStrongestFollowerDiedLastRound = strongestDeadFollower,
            playerInitiatedCombat = playerGetsToken,
            blockCommitted = false,
            playerCastSpellInBlock = false,
            enemyCastBurstInBlock = false,
            playerFastSpellUsedInCombat = false,
            enemyFastSpellUsedInCombat = false,
            playerHand = cleanHand,
            playerCardsPlayedThisRound = 0,
            playerSpellsPlayedThisRound = 0,
            playerPlunderActive = false,
            followersPlayedThisRound = 0,
            renewingPowerUsedThisRound = false,
            stoleEnemyCreatedCardThisRound = false,
            enemySummonedThisRound = false,
            attackingUnitIds = emptyList(),
            blockPairings = emptyMap(),
            confirmedBlockedAttackerIds = emptySet(),
            challengedPairings = emptyMap(),
            lockedBlockAttackerIds = emptySet(),
            selectedAttackerForChallenger = null,
            selectedEnemyAttackerForBlock = null,
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = roundInitiator,
            consecutivePasses = 0,
            consecutiveAiActionsInRound = 0,
            lastStackCaster = null,
            combatLog = logs.takeLast(16)
        )

        s = PowerResolver.onRoundEndPostAdvance(s, logs)

        if (playerGetsToken) {
            s = grantPlayerAttackToken(s, logs, "Đầu Vòng Đấu")
        }

        s = drawPlayerCard(s)
        s = drawEnemyCard(s)
        s = applyRoundStartHooks(s, isGameStart = false)
        return s
    }

    internal fun checkChampionLevelUps(
        state: CombatState,
        logs: MutableList<String>,
        isRoundStart: Boolean = false
    ): CombatState {
        var s = state
        var rallyTriggered = false
        var stunAllTriggered = false
        var refillSpellManaTriggered = false
        var refillManaBonus = 0
        val newlyLeveledIds = mutableListOf<String>()
        val updatedBoard = s.playerBoard.map { u ->
            if (u.template.isChampion && !u.isLeveledUp && !u.isSilenced) {
                val shouldLevel = when (u.template.id) {
                    "card_jinx" -> s.playerHand.isEmpty() || s.playerSpellsPlayedThisRound >= 2
                    "card_lux" -> s.playerSpellsManaSpent >= 6
                    "card_yasuo" -> s.enemiesStunnedCount >= 3 || s.round >= 3
                    "card_ashe" -> s.enemiesFrostbittenCount >= 3 || s.round >= 3
                    "card_garen" -> u.levelProgress >= 2
                    "card_anivia" -> isRoundStart && s.isEnlightened
                    "card_tryndamere" -> false
                    "card_renekton" -> u.levelProgress >= 2
                    "card_azir" -> s.round >= 4
                    "card_nasus" -> s.playerSlainCount >= 10
                    "card_galio" -> s.playerBoard.filter { it.template.type != CardType.LANDMARK }.sumOf { it.health } >= 25
                    else -> s.round >= 3
                }
                if (shouldLevel) {
                    logs.add("★ ${u.template.name} ĐÃ THĂNG CẤP! (+${u.template.levelUpPowerBonus}|+${u.template.levelUpHealthBonus})")
                    newlyLeveledIds.add(u.template.id)
                    if (s.equippedRelics.any { it.levelUpRally }) rallyTriggered = true
                    if (s.equippedRelics.any { it.id == "R0031" }) stunAllTriggered = true
                    if (s.equippedRelics.any { it.id == "R0113" }) refillSpellManaTriggered = true
                    if (s.equippedRelics.any { it.id == "R0105" }) refillManaBonus += 3
                    val extraKws = if (u.template.id == "card_nasus") setOf(Keyword.SPELLSHIELD) else emptySet()
                    u.copy(
                        isLeveledUp = true,
                        power = u.power + u.template.levelUpPowerBonus,
                        health = u.health + u.template.levelUpHealthBonus,
                        maxHealth = u.maxHealth + u.template.levelUpHealthBonus,
                        keywords = u.keywords + extraKws,
                        hasSpellShield = u.hasSpellShield || extraKws.contains(Keyword.SPELLSHIELD)
                    )
                } else u
            } else u
        }
        s = s.copy(playerBoard = updatedBoard)
        if (rallyTriggered) {
            s = grantPlayerAttackToken(s, logs, "Crownguard Inheritance")
            logs.add("Crownguard Inheritance kích hoạt Tập Hợp!")
        }
        if (stunAllTriggered) {
            s.enemyBoard.toList().forEach { e ->
                s = stunEnemyUnit(s, e.instanceId, logs)
            }
            logs.add("Tempest Blade làm Choáng toàn bộ kẻ địch!")
        }
        if (refillSpellManaTriggered) {
            s = s.copy(playerSpellMana = 3)
        }
        if (refillManaBonus > 0) {
            s = s.copy(playerMana = minOf(s.playerMaxMana, s.playerMana + refillManaBonus))
        }
        if (newlyLeveledIds.isNotEmpty() && s.equippedRelics.any { it.id == "R0130" } && s.playerBoard.size < 6) {
            val summonId = when {
                s.playerBoard.none { it.template.id == "card_lux" } -> "card_lux"
                s.playerBoard.none { it.template.id == "card_garen" } -> "card_garen"
                else -> null
            }
            if (summonId != null) {
                s = summonPlayerUnit(s, createHandInstance(CardCatalog.getCard(summonId)), logs).first
                logs.add("Protectors of Demacia triệu hồi ${CardCatalog.getCard(summonId).name}!")
            }
        }
        // P0671: Những Người Bảo Hộ Demacia - Khi Thăng Cấp 1 Tướng, triệu hồi Lux -> Garen -> Galio
        if (newlyLeveledIds.isNotEmpty()) {
            s = PowerResolver.onChampionLevelUp(s, logs)
        }
        // Đĩa Mặt Trời Chôn Vùi: Khi Tướng Shurima Thăng Cấp, đẩy nhanh 10 vòng
        val hasShurimaLevelUp = newlyLeveledIds.any { CardCatalog.getCard(it).region == Region.SHURIMA }
        if (hasShurimaLevelUp) {
            val sunDiscs = s.playerBoard.filter { it.template.id == "card_buried_sun_disc" && it.countdownRemaining > 0 }
            sunDiscs.forEach { disc ->
                val rem = disc.countdownRemaining - 10
                if (rem <= 0) {
                    s = s.copy(playerBoard = s.playerBoard.filterNot { it.instanceId == disc.instanceId })
                    s = completeCountdownLandmark(s, disc, logs)
                } else {
                    s = s.copy(playerBoard = s.playerBoard.map {
                        if (it.instanceId == disc.instanceId) it.copy(countdownRemaining = rem) else it
                    })
                }
            }
        }
        for (champId in newlyLeveledIds) {
            when (champId) {
                "card_jinx" -> {
                    s = dealDamageToEnemyNexus(s, 4, logs)
                    if (s.playerHand.size < 10) {
                        s = s.copy(
                            playerHand = s.playerHand + createHandInstance(
                                CardCatalog.getCard("card_super_mega_rocket"),
                                isFleeting = true
                            )
                        )
                    }
                    logs.add("Jinx Thăng Cấp gây 4 sát thương Nhà Chính & tạo Tên Lửa Đạn Đạo Siêu Khủng Khiếp!")
                }
                "card_lux" -> {
                    if (s.playerHand.size < 10) {
                        s = s.copy(
                            playerHand = s.playerHand + createHandInstance(
                                CardCatalog.getCard("card_final_spark"),
                                isFleeting = true
                            )
                        )
                    }
                    logs.add("Lux Thăng Cấp tạo Cầu Vồng Tối Thượng 0 tiêu hao!")
                }
            }
        }
        return s
    }

    internal fun onPlayerSkillOrElementalPlayed(
        state: CombatState,
        logs: MutableList<String>,
        sourceName: String
    ): CombatState {
        var s = state.copy(playerElementalAndSkillsPlayed = state.playerElementalAndSkillsPlayed + 1)

        // Anivia 3★ Ân Huệ Mùa Đông II: Nhận 1 ngọc năng lượng rỗng
        if (s.championId == "champ_anivia" && s.starLevel >= 3) {
            s = gainPlayerManaGem(s, empty = true, logs, "Ân Huệ Mùa Đông II")
        }
        return s
    }

    internal fun gainPlayerManaGem(
        state: CombatState,
        empty: Boolean,
        logs: MutableList<String>,
        sourceLabel: String = "Tăng Ngọc Năng Lượng"
    ): CombatState {
        var s = state
        if (empty) {
            if (s.isEnlightened && s.championId == "champ_anivia" && s.starLevel >= 2) {
                if (s.playerBoard.isNotEmpty()) {
                    s = s.copy(
                        playerBoard = s.playerBoard.map {
                            it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                        }
                    )
                    logs.add("Băng Giá Tràn Trề (Khai Sáng): Toàn bộ đồng minh nhận +1|+1!")
                }
                return s
            }
            val nextPending = (s.pendingEmptyManaGems + 1).coerceAtMost(10)
            s = s.copy(pendingEmptyManaGems = nextPending)
            logs.add("$sourceLabel: Nhận +1 ngọc năng lượng rỗng (+$nextPending Mana tạm thời cho vòng sau).")
            return s
        }

        val currentPermanentMax = (s.playerMaxMana - s.playerTempManaFromEmptyGems).coerceIn(1, 10)
        if (s.playerMaxMana >= 10 && currentPermanentMax >= 10) {
            // Anivia 2★ Băng Giá Tràn Trề: Nếu được Khai Sáng, ban +1|+1 cho đồng minh
            if (s.isEnlightened && s.championId == "champ_anivia" && s.starLevel >= 2 && s.playerBoard.isNotEmpty()) {
                s = s.copy(
                    playerBoard = s.playerBoard.map {
                        it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                    }
                )
                logs.add("Băng Giá Tràn Trề (Khai Sáng): Toàn bộ đồng minh nhận +1|+1!")
            }
            return s
        }
        val newMax = minOf(10, s.playerMaxMana + 1)
        val newCurrent = minOf(newMax, s.playerMana + 1)
        val nextTemp = (s.playerTempManaFromEmptyGems - (if (s.playerMaxMana >= 10) 1 else 0)).coerceAtLeast(0)
        s = s.copy(
            playerMaxMana = newMax,
            playerMana = newCurrent,
            playerTempManaFromEmptyGems = nextTemp
        )
        logs.add("$sourceLabel: Nhận +1 ngọc năng lượng (Tối đa: $newMax).")
        return s
    }

    internal fun applyDeepBoardBuffs(state: CombatState, logs: MutableList<String>): CombatState {
        if (state.playerDrawPile.size > 15) return state
        var changed = false
        val updated = state.playerBoard.map { u ->
            if (!u.isSilenced && u.keywords.contains(Keyword.DEEP) && !u.hasDeepBonusApplied) {
                changed = true
                logs.add("${u.template.name} đạt Thẩm Sâu (+3|+3)!")
                u.copy(
                    power = u.power + 3,
                    health = u.health + 3,
                    maxHealth = u.maxHealth + 3,
                    hasDeepBonusApplied = true
                )
            } else u
        }
        return if (changed) state.copy(playerBoard = updated) else state
    }

    internal fun refreshEvolveAndSeenKeywords(state: CombatState, logs: MutableList<String>): CombatState {
        val boardKeywords = state.playerBoard
            .filterNot { it.isSilenced }
            .flatMap { unit ->
                buildList {
                    unit.keywords.filter { it.isSharablePositive }.forEach { add(it) }
                    if (unit.hasBarrier) add(Keyword.BARRIER)
                    if (unit.hasSpellShield) add(Keyword.SPELLSHIELD)
                    if (unit.hasDeathless) add(Keyword.DEATHLESS)
                }
            }
        val allSeen = state.playerUniqueKeywordsSeen + boardKeywords
        var changed = allSeen != state.playerUniqueKeywordsSeen
        val updatedBoard = if (allSeen.size >= 6) {
            state.playerBoard.map { u ->
                if (!u.isSilenced && u.keywords.contains(Keyword.EVOLVE) && !u.hasEvolveBonusApplied) {
                    changed = true
                    logs.add("${u.template.name} kích hoạt Tiến Hóa (+2|+2)!")
                    u.copy(
                        power = u.power + 2,
                        health = u.health + 2,
                        maxHealth = u.maxHealth + 2,
                        hasEvolveBonusApplied = true
                    )
                } else u
            }
        } else {
            state.playerBoard
        }
        return if (changed) state.copy(playerBoard = updatedBoard, playerUniqueKeywordsSeen = allSeen) else state
    }

    internal fun triggerFatedOnTarget(
        state: CombatState,
        targetUnitId: String,
        sourceIsPlayer: Boolean,
        logs: MutableList<String>
    ): CombatState {
        val board = if (sourceIsPlayer) state.playerBoard else state.enemyBoard
        var triggered = false
        val updated = board.map { u ->
            if (u.instanceId == targetUnitId && !u.isSilenced && u.keywords.contains(Keyword.FATED) && !u.fatedUsedThisRound) {
                triggered = true
                logs.add("${u.template.name} kích hoạt Định Mệnh (+1|+1)!")
                u.copy(
                    power = u.power + 1,
                    health = u.health + 1,
                    maxHealth = u.maxHealth + 1,
                    fatedUsedThisRound = true
                )
            } else u
        }
        if (!triggered) return state
        return if (sourceIsPlayer) state.copy(playerBoard = updated) else state.copy(enemyBoard = updated)
    }

    private fun triggerPlayerLurkIfEligible(
        state: CombatState,
        attackers: List<UnitOnBoard>,
        logs: MutableList<String>
    ): CombatState {
        if (state.playerLurkTriggeredThisRound) return state
        if (attackers.none { !it.isSilenced && it.keywords.contains(Keyword.LURK) }) return state
        val topCard = state.playerDrawPile.firstOrNull() ?: return state
        val topIsLurker = topCard.template.keywords.contains(Keyword.LURK) || topCard.extraKeywords.contains(Keyword.LURK)
        if (!topIsLurker) return state

        logs.add("Ẩn Nấp kích hoạt! Toàn bộ quân Ẩn Nấp nhận +1|+0.")
        return state.copy(
            playerLurkTriggeredThisRound = true,
            playerLurkBonus = state.playerLurkBonus + 1,
            playerBoard = state.playerBoard.map { u ->
                if (!u.isSilenced && u.keywords.contains(Keyword.LURK)) u.copy(power = u.power + 1) else u
            },
            playerHand = state.playerHand.map { hc ->
                if (hc.template.keywords.contains(Keyword.LURK) || hc.extraKeywords.contains(Keyword.LURK)) {
                    hc.copy(bonusPower = hc.bonusPower + 1)
                } else hc
            },
            playerDrawPile = state.playerDrawPile.map { hc ->
                if (hc.template.keywords.contains(Keyword.LURK) || hc.extraKeywords.contains(Keyword.LURK)) {
                    hc.copy(bonusPower = hc.bonusPower + 1)
                } else hc
            }
        )
    }

    internal fun applyAttackSupportBuffs(
        state: CombatState,
        orderedAttackerIds: List<String>,
        playerIsAttacking: Boolean,
        logs: MutableList<String>
    ): CombatState {
        if (orderedAttackerIds.size < 2) return state
        var s = state
        val roundPow = s.roundSupportPowerBuffs.toMutableMap()
        val roundHp = s.roundSupportHealthBuffs.toMutableMap()
        val roundKw = s.roundSupportKeywords.toMutableMap()

        for (i in 0 until orderedAttackerIds.size - 1) {
            val board = if (playerIsAttacking) s.playerBoard else s.enemyBoard
            val supporter = board.find { it.instanceId == orderedAttackerIds[i] } ?: continue
            if (supporter.isSilenced || !supporter.keywords.contains(Keyword.SUPPORT)) continue
            val rightId = orderedAttackerIds[i + 1]
            val supported = board.find { it.instanceId == rightId } ?: continue

            val hasShadeleaf = playerIsAttacking && supporter.template.isChampion && s.equippedRelics.any { it.id == "R0041" }
            val hasCorruptedStar = playerIsAttacking && supporter.template.isChampion && s.equippedRelics.any { it.id == "R0045" }

            val grantPow = supporter.supportPowerBonus
            val grantHp = supporter.supportHealthBonus
            val grantKw = supporter.supportKeyword ?: if (hasShadeleaf) Keyword.ELUSIVE else null
            if (grantPow != 0 || grantHp != 0 || grantKw != null) {
                val updatedBoard = board.map { u ->
                    if (u.instanceId == rightId) {
                        val hadKwAlready = grantKw != null && u.keywords.contains(grantKw)
                        if (grantKw != null && !hadKwAlready) {
                            roundKw[rightId] = (roundKw[rightId] ?: emptySet()) + grantKw
                        }
                        if (grantPow != 0) roundPow[rightId] = (roundPow[rightId] ?: 0) + grantPow
                        if (grantHp != 0) roundHp[rightId] = (roundHp[rightId] ?: 0) + grantHp
                        u.copy(
                            power = u.power + grantPow,
                            health = u.health + grantHp,
                            maxHealth = u.maxHealth + grantHp,
                            keywords = if (grantKw != null) u.keywords + grantKw else u.keywords,
                            hasBarrier = u.hasBarrier || grantKw == Keyword.BARRIER,
                            hasSpellShield = u.hasSpellShield || grantKw == Keyword.SPELLSHIELD,
                            hasDeathless = u.hasDeathless || grantKw == Keyword.DEATHLESS
                        )
                    } else u
                }
                s = if (playerIsAttacking) s.copy(playerBoard = updatedBoard) else s.copy(enemyBoard = updatedBoard)
                logs.add("${supporter.template.name} Hỗ Trợ ${supported.template.name} (+$grantPow|+$grantHp${grantKw?.let { ", ${it.displayName}" } ?: ""})!")
            }

            if (hasCorruptedStar) {
                val liveSup = s.playerBoard.find { it.instanceId == supporter.instanceId }
                val liveTarget = s.playerBoard.find { it.instanceId == rightId }
                if (liveSup != null && liveTarget != null) {
                    val absorbedKws = liveTarget.keywords.filter { it.isSharablePositive }.toSet()
                    s = s.copy(playerBoard = s.playerBoard.map { u ->
                        if (u.instanceId == liveSup.instanceId) {
                            u.copy(
                                power = u.power + liveTarget.effectivePower,
                                health = u.health + liveTarget.health,
                                maxHealth = u.maxHealth + liveTarget.maxHealth,
                                keywords = u.keywords + absorbedKws
                            )
                        } else u
                    })
                    s = removeUnit(s, liveTarget.instanceId, isEnemy = false, logs = logs)
                    logs.add("Corrupted Star Fragment hấp thụ ${liveTarget.template.name}!")
                }
            }
        }
        s = s.copy(
            roundSupportPowerBuffs = roundPow,
            roundSupportHealthBuffs = roundHp,
            roundSupportKeywords = roundKw
        )
        if (playerIsAttacking) {
            s = refreshEvolveAndSeenKeywords(s, logs)
        }
        return refreshPluckyPoroEmpowerment(s)
    }

    private fun detachEquipmentFromUnit(unit: UnitOnBoard): Pair<UnitOnBoard, CardTemplate?> {
        val gear = unit.equippedGear ?: return unit to null
        val retainKeywords = buildSet {
            addAll(unit.template.keywords)
            unit.attachedItems.mapNotNull { it.grantedKeyword }.forEach { add(it) }
            unit.supportKeyword?.let { add(it) }
            if (unit.isSilenced) add(Keyword.SILENCE)
        }
        val strippedKeywords = (unit.keywords - gear.keywords) + (unit.keywords.intersect(retainKeywords))
        val cleanMaxHp = maxOf(1, unit.maxHealth - gear.baseHealth)
        val detached = unit.copy(
            power = maxOf(0, unit.power - gear.basePower),
            maxHealth = cleanMaxHp,
            health = minOf(cleanMaxHp, maxOf(1, unit.health)),
            keywords = strippedKeywords,
            equippedGear = null,
            equippedGearName = null
        )
        return detached to gear
    }

    private fun returnEquipmentToHand(
        state: CombatState,
        gear: CardTemplate?,
        toPlayer: Boolean,
        logs: MutableList<String>
    ): CombatState {
        if (gear == null) return state
        return if (toPlayer) {
            if (state.playerHand.size >= 10) state
            else {
                logs.add("Trang bị ${gear.name} trở về tay.")
                state.copy(playerHand = state.playerHand + createHandInstance(gear, isFleeting = false, isCreated = false))
            }
        } else {
            if (state.enemyHand.size >= 10) state
            else {
                logs.add("Trang bị ${gear.name} của địch trở về tay.")
                state.copy(enemyHand = state.enemyHand + gear)
            }
        }
    }

    private fun onUnitStrike(
        state: CombatState,
        strikerId: String,
        isPlayerStriker: Boolean,
        hitNexus: Boolean,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        val board = if (isPlayerStriker) s.playerBoard else s.enemyBoard
        val striker = board.find { it.instanceId == strikerId }
        val updatedBoard = board.map { u ->
            if (u.instanceId == strikerId) u.copy(levelProgress = u.levelProgress + 1) else u
        }
        val isReputation = isPlayerStriker && (striker?.effectivePower ?: 0) >= 5
        s = if (isPlayerStriker) {
            s.copy(
                playerBoard = updatedBoard,
                playerTotalStrikes = s.playerTotalStrikes + 1,
                playerReputationStrikes = s.playerReputationStrikes + if (isReputation) 1 else 0,
                playerNexusStrikesCount = s.playerNexusStrikesCount + if (hitNexus) 1 else 0
            )
        } else {
            s.copy(
                enemyBoard = updatedBoard,
                enemyTotalStrikes = s.enemyTotalStrikes + 1,
                enemyNexusStrikesCount = s.enemyNexusStrikesCount + if (hitNexus) 1 else 0
            )
        }
        if (isPlayerStriker) {
            s = checkChampionLevelUps(s, logs)
        }
        return s
    }

    fun startFreeAttack(
        state: CombatState,
        attackerIds: List<String>,
        playerIsAttacking: Boolean,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        var s = state
        val sourceBoard = if (playerIsAttacking) s.playerBoard else s.enemyBoard
        val validAttackers = attackerIds.mapNotNull { id -> sourceBoard.find { it.instanceId == id && it.canAttack } }
        if (validAttackers.isEmpty()) return s
        val validIds = validAttackers.map { it.instanceId }

        if (playerIsAttacking) {
            s = triggerPlayerLurkIfEligible(s, validAttackers, logs)
        }

        s = applyAttackSupportBuffs(s, validIds, playerIsAttacking = playerIsAttacking, logs = logs)

        validAttackers.forEach { atk ->
            if (playerIsAttacking && atk.template.isChampion && s.equippedRelics.any { it.attackGainPlusOnePlusOne }) {
                s = s.copy(playerBoard = s.playerBoard.map {
                    if (it.instanceId == atk.instanceId) it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1) else it
                })
            }
            if (playerIsAttacking && atk.template.isChampion && s.equippedRelics.any { it.id == "R0160" }) {
                s = dealDamageToEnemyNexus(s, 1, logs)
                s.enemyBoard.toList().forEach { e ->
                    s = damageUnit(s, e.instanceId, isEnemy = true, damage = 1, isSpell = false, logs = logs)
                }
            }
            if (playerIsAttacking && atk.template.isChampion && s.equippedRelics.any { it.id == "R0120" }) {
                s = dealDamageToEnemyNexus(s, 2, logs)
            }
            if (playerIsAttacking && atk.template.id == "card_ashe" && !atk.isSilenced) {
                val target = s.enemyBoard.filter { !it.isFrostbitten }.maxByOrNull { it.effectivePower }
                    ?: s.enemyBoard.maxByOrNull { it.effectivePower }
                if (target != null) {
                    s = frostbiteUnit(s, target.instanceId, isEnemy = true, isSpell = false, fromAllyEffect = false, logs = logs)
                }
            }
            if (playerIsAttacking && atk.template.id == "card_yadulsk_snowdog" && !atk.isSilenced) {
                s = grantStatsToDeckUnits(
                    state = s,
                    count = 2,
                    powerGain = 1,
                    healthGain = 1,
                    logs = logs,
                    sourceLabel = atk.template.name
                )
            }
        }
        if (playerIsAttacking && validAttackers.isNotEmpty() && s.championId == "champ_tryndamere" && s.starLevel >= 2) {
            val allyIndices = s.playerDrawPile.mapIndexedNotNull { idx, c ->
                if (c.template.type == CardType.UNIT || c.template.type == CardType.CHAMPION) idx else null
            }
            if (allyIndices.isNotEmpty()) {
                val chosenIdx = allyIndices.random()
                val chosen = s.playerDrawPile[chosenIdx]
                val newPile = s.playerDrawPile.toMutableList().apply {
                    this[chosenIdx] = chosen.copy(bonusPower = chosen.bonusPower + 1, bonusHealth = chosen.bonusHealth + 1)
                }
                s = s.copy(playerDrawPile = newPile)
                logs.add("Say Máu (Tryndamere 2★): Ban +1|+1 cho ${chosen.template.name} trong bộ bài!")
            }
        }

        if (!playerIsAttacking) {
            s = applyEnemyAttackConstellationPowers(s, validIds, logs)
        }

        val attackSkills = mutableListOf<SpellStackEntry>()
        if (playerIsAttacking) {
            validAttackers.forEach { atk ->
                val liveAtk = s.playerBoard.find { it.instanceId == atk.instanceId }
                if (liveAtk != null && liveAtk.template.id == "card_anivia" && !liveAtk.isSilenced && !liveAtk.isStunned) {
                    val baseStormDmg = if (liveAtk.isLeveledUp) 2 else 1
                    val skillCard = CardCatalog.getCard("skill_glacial_storm").copy(effectValue = baseStormDmg)
                    s = onPlayerSkillOrElementalPlayed(s, logs, skillCard.name)
                    attackSkills.add(
                        SpellStackEntry(
                            id = "st_${nextId()}",
                            sourceIsPlayer = true,
                            card = skillCard,
                            targetUnitId = null,
                            damageBonus = 0
                        )
                    )
                    logs.add("Kỹ năng ${skillCard.name} của Anivia vào hàng chờ!")
                }
            }
        }

        val sandSoldierAtkCount = validAttackers.count { it.template.id == "card_sand_soldier" && !it.isSilenced }
        if (playerIsAttacking && sandSoldierAtkCount > 0) {
            s = dealDamageToEnemyNexus(s, sandSoldierAtkCount, logs)
        }

        val currentBoard = if (playerIsAttacking) s.playerBoard else s.enemyBoard
        val survivingIds = validIds.filter { id -> currentBoard.any { it.instanceId == id && !it.isStunned } }
        if (survivingIds.isEmpty()) return s

        logs.add("Tấn Công Tự Do với ${survivingIds.size} quân!")
        return s.copy(
            isFreeAttack = true,
            playerInitiatedCombat = playerIsAttacking,
            blockCommitted = false,
            playerCastSpellInBlock = false,
            enemyCastBurstInBlock = false,
            playerFastSpellUsedInCombat = false,
            enemyFastSpellUsedInCombat = false,
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = if (playerIsAttacking) PriorityHolder.ENEMY else PriorityHolder.PLAYER,
            consecutivePasses = 0,
            lastStackCaster = if (attackSkills.isNotEmpty()) PriorityHolder.PLAYER else s.lastStackCaster,
            spellStack = s.spellStack + attackSkills,
            attackingUnitIds = survivingIds,
            blockPairings = emptyMap(),
            confirmedBlockedAttackerIds = emptySet(),
            challengedPairings = emptyMap(),
            lockedBlockAttackerIds = emptySet(),
            selectedAttackerForChallenger = null,
            selectedEnemyAttackerForBlock = null,
            combatLog = logs.takeLast(16)
        )
    }

    fun queueSkillToStack(
        state: CombatState,
        skillCard: CardTemplate,
        sourceIsPlayer: Boolean,
        targetUnitId: String? = null,
        bonusDmg: Int = 0,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        val entry = SpellStackEntry(
            id = "st_${nextId()}",
            sourceIsPlayer = sourceIsPlayer,
            card = skillCard,
            targetUnitId = targetUnitId,
            damageBonus = bonusDmg
        )
        var s = state
        if (sourceIsPlayer) {
            s = onPlayerSkillOrElementalPlayed(s, logs, skillCard.name)
        }
        val nextPhase = if (s.phase == CombatPhase.MAIN_ACTION_PHASE) CombatPhase.SPELL_STACK_PHASE else s.phase
        val nextPriority = if (sourceIsPlayer) PriorityHolder.ENEMY else PriorityHolder.PLAYER
        logs.add("Kỹ Năng ${skillCard.name} vào hàng chờ phản hồi.")
        return s.copy(
            spellStack = s.spellStack + entry,
            phase = nextPhase,
            lastStackCaster = if (sourceIsPlayer) PriorityHolder.PLAYER else PriorityHolder.ENEMY,
            consecutivePasses = 0,
            priorityHolder = nextPriority,
            combatLog = logs.takeLast(16)
        )
    }

    internal fun checkGameOver(state: CombatState): CombatState {
        return when {
            state.enemyNexusHp <= 0 -> state.copy(phase = CombatPhase.GAME_OVER, playerWon = true)
            state.playerNexusHp <= 0 -> state.copy(phase = CombatPhase.GAME_OVER, playerWon = false)
            else -> state
        }
    }

    internal fun generateId(): String = nextId()

    // --- 55 EPIC POWER HELPERS ---

    internal fun attachItemToHandCard(card: HandCardInstance, item: ItemTemplate): HandCardInstance {
        val updatedItems = card.attachedItems + item
        val newKeywords = item.grantedKeyword?.let { card.extraKeywords + it } ?: card.extraKeywords
        return card.copy(
            attachedItems = updatedItems,
            effectiveCost = maxOf(0, card.effectiveCost - item.costReduction),
            bonusPower = card.bonusPower + item.bonusPower,
            bonusHealth = card.bonusHealth + item.bonusHealth,
            extraKeywords = newKeywords
        )
    }

    internal fun grantPlayerAttackToken(
        state: CombatState,
        logs: MutableList<String>,
        reason: String? = null
    ): CombatState {
        val gainedNewToken = !state.playerHasAttackToken
        var s = state.copy(playerHasAttackToken = true)
        if (gainedNewToken) {
            s = PowerResolver.onAttackTokenGained(s, logs)
        }
        return s
    }

    internal fun completeCountdownLandmark(
        state: CombatState,
        landmark: UnitOnBoard,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        when (landmark.template.id) {
            "card_buried_sun_disc" -> {
                s = s.copy(playerBoard = s.playerBoard.map { u ->
                    if (u.template.isChampion && u.template.region == Region.SHURIMA && !u.isSilenced) {
                        if (!u.isLeveledUp) {
                            u.copy(
                                isLeveledUp = true,
                                power = u.power + u.template.levelUpPowerBonus,
                                health = u.health + u.template.levelUpHealthBonus,
                                maxHealth = u.maxHealth + u.template.levelUpHealthBonus
                            )
                        } else {
                            u.copy(power = u.power + 1, health = u.health + 1, maxHealth = u.maxHealth + 1)
                        }
                    } else u
                })
                logs.add("Đĩa Mặt Trời Chôn Vùi khôi phục! Thăng Cấp các Tướng Shurima!")
            }
            "card_hibernating_rockbear" -> {
                if (s.playerBoard.size < 6) {
                    s = summonPlayerUnit(s, createHandInstance(CardCatalog.getCard("card_grumpy_rockbear")), logs).first
                }
                logs.add("Gấu Đá Ngủ Đông thức giấc thành Gấu Đá Khổng Lồ (5|4)!")
            }
            "card_ancient_preparations" -> {
                s = drawPlayerCard(s)
                logs.add("Chuẩn Bị Cổ Xưa hoàn tất Đếm Ngược: Rút 1 lá!")
            }
            else -> {
                s = s.copy(playerSpellMana = minOf(3, s.playerSpellMana + 2))
                repeat(2) { s = drawPlayerCard(s) }
                logs.add("${landmark.template.name} hoàn tất Đếm Ngược: Rút 2 lá & +2 Năng Lượng Phép!")
            }
        }
        return s
    }

    internal fun addEnemyCreatedCardToHand(
        state: CombatState,
        card: CardTemplate,
        logs: MutableList<String>
    ): CombatState {
        val (stolenState, intercepted) = PowerResolver.onEnemyCreatedCard(state, card, logs)
        if (intercepted) return stolenState
        return if (state.enemyHand.size < 10) {
            state.copy(enemyHand = state.enemyHand + card)
        } else state
    }

    internal fun triggerPlayerTrapOnEnemyDraw(
        state: CombatState,
        drawnCard: CardTemplate,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        if (s.enemyDeckTrapsCount <= 0) return s
        val trapsTriggered = minOf(s.enemyDeckTrapsCount, Random.nextInt(1, 3))
        s = s.copy(enemyDeckTrapsCount = maxOf(0, s.enemyDeckTrapsCount - trapsTriggered))
        s = dealDamageToEnemyNexus(s, trapsTriggered, logs)
        logs.add("Bẫy Nấm Độc kích hoạt trên ${drawnCard.name} của địch gây $trapsTriggered sát thương!")
        return PowerResolver.onTrapTriggeredOnEnemyDraw(s, drawnCard, trapsTriggered, logs)
    }

    internal fun isAllyOnlySpell(state: CombatState, card: CardTemplate): Boolean {
        val (enemies, allies) = getValidSpellTargetIds(state, card)
        return (enemies.isEmpty() && allies.isNotEmpty()) ||
            card.targeting.targetSide == TargetSide.ALLY ||
            card.effectType == CardEffectType.FREE_ATTACK ||
            card.effectType == CardEffectType.BUFF_ALLY_STATS
    }

    internal fun levelUpSpecificChampion(
        state: CombatState,
        unitInstanceId: String,
        logs: MutableList<String>
    ): CombatState {
        val target = state.playerBoard.find { it.instanceId == unitInstanceId } ?: return state
        if (!target.template.isChampion || target.isLeveledUp || target.isSilenced) return state
        var s = state
        val extraKws = if (target.template.id == "card_nasus") setOf(Keyword.SPELLSHIELD) else emptySet()
        s = s.copy(playerBoard = s.playerBoard.map { u ->
            if (u.instanceId == unitInstanceId) {
                if (u.template.id == "card_tryndamere") {
                    val extraPow = maxOf(0, u.power - u.template.basePower)
                    val extraHp = maxOf(0, u.maxHealth - u.template.baseHealth)
                    u.copy(
                        isLeveledUp = true,
                        power = 9 + extraPow,
                        health = 9 + extraHp,
                        maxHealth = 9 + extraHp,
                        keywords = (u.keywords - Keyword.EPHEMERAL) + setOf(
                            Keyword.OVERWHELM,
                            Keyword.TOUGH,
                            Keyword.FEARSOME
                        )
                    )
                } else {
                    u.copy(
                        isLeveledUp = true,
                        power = u.power + u.template.levelUpPowerBonus,
                        health = u.health + u.template.levelUpHealthBonus,
                        maxHealth = u.maxHealth + u.template.levelUpHealthBonus,
                        keywords = u.keywords + extraKws,
                        hasSpellShield = u.hasSpellShield || extraKws.contains(Keyword.SPELLSHIELD)
                    )
                }
            } else u
        })
        logs.add("★ Ánh Hào Quang Tinh Tú thăng cấp ${target.template.name}!")
        if (s.equippedRelics.any { it.levelUpRally }) {
            s = grantPlayerAttackToken(s, logs, "Crownguard Inheritance")
        }
        if (s.equippedRelics.any { it.id == "R0031" }) {
            s.enemyBoard.toList().forEach { e -> s = stunEnemyUnit(s, e.instanceId, logs) }
        }
        if (s.equippedRelics.any { it.id == "R0113" }) {
            s = s.copy(playerSpellMana = 3)
        }
        if (s.equippedRelics.any { it.id == "R0105" }) {
            s = s.copy(playerMana = minOf(s.playerMaxMana, s.playerMana + 3))
        }
        s = PowerResolver.onChampionLevelUp(s, logs)
        if (target.template.region == Region.SHURIMA) {
            s.playerBoard.filter { it.template.id == "card_buried_sun_disc" && it.countdownRemaining > 0 }.forEach { disc ->
                val rem = disc.countdownRemaining - 10
                if (rem <= 0) {
                    s = s.copy(playerBoard = s.playerBoard.filterNot { it.instanceId == disc.instanceId })
                    s = completeCountdownLandmark(s, disc, logs)
                } else {
                    s = s.copy(playerBoard = s.playerBoard.map {
                        if (it.instanceId == disc.instanceId) it.copy(countdownRemaining = rem) else it
                    })
                }
            }
        }
        return s
    }

    internal fun pickThreeFollowerTransformOptions(targetCost: Int): List<String> {
        val allFollowers = CardCatalog.cards.values.filter { it.type == CardType.UNIT }
        val exact = allFollowers.filter { it.cost == targetCost }.shuffled()
        val chosen = exact.take(3).toMutableList()
        if (chosen.size < 3) {
            val fallback = allFollowers
                .filterNot { candidate -> chosen.any { it.id == candidate.id } }
                .sortedBy { kotlin.math.abs(it.cost - targetCost) }
            for (c in fallback) {
                if (chosen.size >= 3) break
                chosen.add(c)
            }
        }
        return chosen.map { it.id }
    }

    internal fun findRandomFollowerWithDelta(targetCost: Int): CardTemplate {
        val followers = CardCatalog.cards.values.filter { it.type == CardType.UNIT }
        val exact = followers.filter { it.cost == targetCost }
        if (exact.isNotEmpty()) return exact.random()
        val minDiff = followers.minOfOrNull { kotlin.math.abs(it.cost - targetCost) } ?: 0
        return followers.filter { kotlin.math.abs(it.cost - targetCost) == minDiff }.random()
    }

    internal fun applyPlayerBoardAuras(unit: UnitOnBoard, state: CombatState): UnitOnBoard =
        PowerResolver.applyPlayerBoardAuras(unit, state)

    internal fun transformPlayerUnitToTemplate(
        state: CombatState,
        unitInstanceId: String,
        newTemplate: CardTemplate,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        val existing = s.playerBoard.find { it.instanceId == unitInstanceId } ?: return s
        val oldTmpl = existing.template
        val oldAuraBonus = PowerResolver.boardAuraStatBonus(existing, s)
        val deltaPow = newTemplate.basePower - oldTmpl.basePower
        val deltaHp = newTemplate.baseHealth - oldTmpl.baseHealth
        val basePow = maxOf(0, existing.power - oldAuraBonus + deltaPow)
        val baseMaxHp = maxOf(1, existing.maxHealth - oldAuraBonus + deltaHp)
        val baseHp = existing.health - oldAuraBonus + deltaHp
        val hadToughOnlyFromP0699 = PowerResolver.lostToughOnlyFromP0699(existing, newTemplate, s)
        val newKeywords = ((existing.keywords - oldTmpl.keywords) + newTemplate.keywords).let { kws ->
            if (hadToughOnlyFromP0699) kws - Keyword.TOUGH else kws
        }
        s = s.copy(
            playerBoard = s.playerBoard.map { u ->
                if (u.instanceId == unitInstanceId) {
                    val preAura = u.copy(
                        template = newTemplate,
                        power = basePow,
                        health = baseHp,
                        maxHealth = baseMaxHp,
                        keywords = newKeywords,
                        hasBarrier = u.hasBarrier || newTemplate.keywords.contains(Keyword.BARRIER),
                        hasSpellShield = u.hasSpellShield || newTemplate.keywords.contains(Keyword.SPELLSHIELD),
                        hasDeathless = u.hasDeathless || newTemplate.keywords.contains(Keyword.DEATHLESS),
                        isLeveledUp = false
                    )
                    val withAuras = PowerResolver.applyPlayerBoardAuras(preAura, s)
                    val clampedMaxHp = maxOf(1, withAuras.maxHealth)
                    val clampedHp = maxOf(1, minOf(clampedMaxHp, withAuras.health))
                    withAuras.copy(
                        maxHealth = clampedMaxHp,
                        health = clampedHp
                    )
                } else u
            }
        )
        s = refreshEvolveAndSeenKeywords(s, logs)
        return PowerResolver.onTransformToDarkin(s, newTemplate, unitInstanceId, logs)
    }

    fun resolveFollowerTransformChoice(state: CombatState, chosenCardId: String): CombatState {
        val pending = state.pendingTransformChoice ?: return state
        if (!pending.optionCardIds.contains(chosenCardId)) return state
        val newTemplate = CardCatalog.findCardOrNull(chosenCardId) ?: return state
        val logs = state.combatLog.toMutableList()
        var s = transformPlayerUnitToTemplate(state, pending.targetUnitInstanceId, newTemplate, logs)
        logs.add("Dòng Thời Gian Hội Tụ biến đổi thành ${newTemplate.name} (${newTemplate.basePower}|${newTemplate.baseHealth})!")
        s = s.copy(
            pendingTransformChoice = null,
            priorityHolder = PriorityHolder.ENEMY,
            consecutivePasses = 0,
            combatLog = logs.takeLast(16)
        )
        s = syncChampionSpellsInHand(s)
        return checkGameOver(s)
    }

    fun resolveHandShuffleChoice(state: CombatState, chosenHandInstanceId: String): CombatState {
        val pending = state.pendingHandShuffleChoice ?: return state
        val chosenCard = state.playerHand.find { it.instanceId == chosenHandInstanceId } ?: return state
        val logs = state.combatLog.toMutableList()

        val remainingHand = state.playerHand.filterNot { it.instanceId == chosenHandInstanceId }
        val updatedPile = state.playerDrawPile.toMutableList().apply {
            add(if (isEmpty()) 0 else Random.nextInt(size + 1), chosenCard.copy(keepAsUnitInHand = false))
        }
        logs.add("${pending.sourceSpellName}: Xáo ${chosenCard.template.name} vào vị trí ngẫu nhiên trong bộ bài.")

        var nextHand = remainingHand
        if (updatedPile.isNotEmpty()) {
            val drawn = updatedPile.removeAt(Random.nextInt(updatedPile.size))
            // Giữ nguyên dạng bài quân nếu rút trúng Tướng qua Lệnh Nhập Ngũ để có thể gọi 2+ Tướng lên sân
            val keptDrawn = if (drawn.template.isChampion) drawn.copy(keepAsUnitInHand = true) else drawn
            if (nextHand.size < 10) {
                nextHand = nextHand + keptDrawn
                logs.add("${pending.sourceSpellName}: Rút ngẫu nhiên ${keptDrawn.template.name}!")
            } else {
                logs.add("${pending.sourceSpellName}: Tay đầy (10 lá), ${keptDrawn.template.name} bị thiêu rụi!")
            }
        }

        var s = state.copy(
            playerHand = nextHand,
            playerDrawPile = updatedPile,
            pendingHandShuffleChoice = null
        )

        // Tryndamere 6★: Tập Hợp (chỉ nhận Attack Token nếu chưa có; giữ nguyên lượt địch nếu địch đang có)
        if (s.championId == "champ_tryndamere" && s.starLevel >= 6 && !s.playerHasAttackToken) {
            s = grantPlayerAttackToken(s, logs, "Hiệp Ước Huyết Thống")
            logs.add("Hiệp Ước Huyết Thống (Tryndamere 6★): Nhận lượt tiến công!")
        }

        s = syncChampionSpellsInHand(s)
        return checkGameOver(s.copy(combatLog = logs.takeLast(16)))
    }
}
