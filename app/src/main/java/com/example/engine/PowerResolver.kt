package com.example.engine

import com.example.model.CardCatalog
import com.example.model.CardEffectType
import com.example.model.CardTemplate
import com.example.model.CardType
import com.example.model.HandCardInstance
import com.example.model.Keyword
import com.example.model.PowerTemplate
import com.example.model.Rarity
import com.example.model.UnitOnBoard
import kotlin.random.Random

/**
 * Resolves all PoC Power triggers and auras across core combat lifecycle hooks:
 * GAME_START, ROUND_START, ROUND_END, ON_SUMMON_ALLY, ON_PLAY_CARD, ON_ALLY_DEATH,
 * plus combat damage / trap / attack-token / level-up power triggers.
 */
internal object PowerResolver {

    data class SummonStatModification(
        val power: Int,
        val health: Int,
        val keywords: Set<Keyword>,
        val grandPlazaBonusPow: Int,
        val grandPlazaBonusHp: Int,
        val grandPlazaAddedKeywords: Set<Keyword>
    )

    fun startingSpellMana(powers: List<PowerTemplate>): Int =
        if (powers.any { it.id == "pow_sorcery" }) 3 else 0

    // --- 1. GAME_START & ROUND_START ---

    fun onGameStart(state: CombatState, logs: MutableList<String>): CombatState {
        var s = state

        // pow_manaflow: Dòng Chảy Năng Lượng Hoàn Mỹ (+2 ngọc năng lượng)
        repeat(s.activePowers.count { it.id == "pow_manaflow" } * 2) {
            s = CombatEngine.gainPlayerManaGem(s, empty = false, logs, "Dòng Chảy Năng Lượng Hoàn Mỹ")
        }

        // P0067: Dòng Chảy Năng Lượng (+1 ngọc năng lượng)
        repeat(s.activePowers.count { it.id == "P0067" }) {
            s = CombatEngine.gainPlayerManaGem(s, empty = false, logs, "Dòng Chảy Năng Lượng")
        }

        // P0101: Xuất Cổng Thần Tốc - Triệu hồi 1 bài quân 2 tiêu hao từ bộ bài
        repeat(s.activePowers.count { it.id == "P0101" }) {
            val twoCostIdx = s.playerDrawPile.indexOfFirst {
                (it.template.type == CardType.UNIT || it.template.type == CardType.CHAMPION) &&
                    it.template.cost == 2
            }
            if (twoCostIdx >= 0 && s.playerBoard.size < 6) {
                val drawn = s.playerDrawPile[twoCostIdx]
                val nextPile = s.playerDrawPile.toMutableList().apply { removeAt(twoCostIdx) }
                s = s.copy(playerDrawPile = nextPile)
                s = CombatEngine.summonPlayerUnit(s, drawn, logs).first
                logs.add("Xuất Cổng Thần Tốc triệu hồi ${drawn.template.name} từ bộ bài!")
            }
        }

        // P0057: Nguồn Năng Lượng Dự Phòng - Triệu hồi 1 Đài Quan Sát Hextech
        repeat(s.activePowers.count { it.id == "P0057" }) {
            if (s.playerBoard.size < 6) {
                s = CombatEngine.summonPlayerUnit(
                    s,
                    CombatEngine.createHandInstance(CardCatalog.getCard("card_hextech_observatory")),
                    logs
                ).first
            }
        }

        // P0226: Sa Mạc Trường Tồn - Triệu hồi Đĩa Mặt Trời Chôn Vùi & tạo Renekton, Azir hoặc Nasus lên tay
        repeat(s.activePowers.count { it.id == "P0226" }) {
            if (s.playerBoard.size < 6) {
                s = CombatEngine.summonPlayerUnit(
                    s,
                    CombatEngine.createHandInstance(CardCatalog.getCard("card_buried_sun_disc")),
                    logs
                ).first
            }
            if (s.playerHand.size < 10) {
                val ascendantId = listOf("card_renekton", "card_azir", "card_nasus").random()
                val champCard = CombatEngine.createHandInstance(CardCatalog.getCard(ascendantId), isCreated = true)
                s = s.copy(playerHand = s.playerHand + champCard)
                logs.add("Sa Mạc Trường Tồn tạo ${champCard.template.name} lên tay!")
            }
        }

        // P1115: Ảnh Hưởng Của Taarosh - Tạo Kích Darkin + 1 trang bị ngẫu nhiên
        repeat(s.activePowers.count { it.id == "P1115" }) {
            if (s.playerHand.size < 10) {
                val darkinEquip = CombatEngine.attachItemToHandCard(
                    CombatEngine.createHandInstance(CardCatalog.getCard("card_forged_blade"), isCreated = true),
                    CardCatalog.items.random()
                )
                s = s.copy(playerHand = s.playerHand + darkinEquip)
                logs.add("Ảnh Hưởng Của Taarosh tạo Kích Darkin kèm trang bị lên tay!")
            }
        }

        // P1122: Vượt Ngục! - Tạo 1 lá Vượt Ngục! lên tay
        repeat(s.activePowers.count { it.id == "P1122" }) {
            if (s.playerHand.size < 10) {
                s = s.copy(
                    playerHand = s.playerHand + CombatEngine.createHandInstance(
                        CardCatalog.getCard("card_breakout"),
                        isCreated = true
                    )
                )
                logs.add("Vượt Ngục! được tạo lên tay.")
            }
        }

        return s
    }

    fun onRoundStart(
        state: CombatState,
        isGameStart: Boolean,
        logs: MutableList<String>
    ): CombatState {
        var s = state

        // pow_sorcery: Khởi đầu vòng với tối đa 3 Năng Lượng Phép
        if (s.activePowers.any { it.id == "pow_sorcery" }) {
            s = s.copy(playerSpellMana = 3)
        }

        // pow_vitality: Sinh Lực Dồi Dào
        if (s.activePowers.any { it.id == "pow_vitality" }) {
            val healed = minOf(s.playerMaxHp, s.playerNexusHp + 2)
            if (healed > s.playerNexusHp) {
                s = s.copy(playerNexusHp = healed)
                logs.add("Sinh Lực Dồi Dào hồi +2 Máu cho Nhà Chính.")
            }
        }

        // P0112: Thống Trị (Epic)
        if (s.activePowers.any { it.id == "P0112" }) {
            s = CombatEngine.grantPlayerAttackToken(s, logs, "Thống Trị")
        }

        if (isGameStart) {
            s = onGameStart(s, logs)
        }

        // P0064: Đứng Yên Đó! - Làm Choáng kẻ địch mạnh nhất
        repeat(s.activePowers.count { it.id == "P0064" }) {
            val strongestEnemy = s.enemyBoard
                .filter { it.template.type != CardType.LANDMARK }
                .filter { !it.isStunned }
                .maxWithOrNull(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health })
                ?: s.enemyBoard
                    .filter { it.template.type != CardType.LANDMARK }
                    .maxWithOrNull(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health })
            if (strongestEnemy != null) {
                s = CombatEngine.stunEnemyUnit(s, strongestEnemy.instanceId, logs)
                logs.add("Đứng Yên Đó! làm Choáng ${strongestEnemy.template.name}.")
            }
        }

        // P0050: Khoan Đã Nào - Làm Choáng đơn vị mạnh nhất và yếu nhất trên bàn
        repeat(s.activePowers.count { it.id == "P0050" }) {
            val allUnits = (s.playerBoard.map { it to false } + s.enemyBoard.map { it to true })
                .filter { it.first.template.type != CardType.LANDMARK }
            val strongest = allUnits.maxWithOrNull(
                compareBy<Pair<UnitOnBoard, Boolean>> { it.first.effectivePower }.thenBy { it.first.health }
            )
            val weakest = allUnits.minWithOrNull(
                compareBy<Pair<UnitOnBoard, Boolean>> { it.first.effectivePower }.thenBy { it.first.health }
            )
            if (strongest != null) {
                s = CombatEngine.stunUnit(s, strongest.first.instanceId, isEnemy = strongest.second, logs = logs)
            }
            if (weakest != null && weakest.first.instanceId != strongest?.first?.instanceId) {
                s = CombatEngine.stunUnit(s, weakest.first.instanceId, isEnemy = weakest.second, logs = logs)
            }
        }

        // P0109: Những Người Bạn Nhỏ - Triệu hồi 1 Poro 1 tiêu hao ngẫu nhiên
        repeat(s.activePowers.count { it.id == "P0109" }) {
            if (s.playerBoard.size < 6) {
                val poroPool = listOf("card_tough_poro", "card_sumpworks_mapper", "card_lonely_poro", "card_nimble_poro")
                val chosenPoro = CardCatalog.getCard(poroPool.random())
                s = CombatEngine.summonPlayerUnit(s, CombatEngine.createHandInstance(chosenPoro), logs).first
            }
        }

        // P0045: Nguồn Cung Ổn Định - Triệu hồi 1 Thùng Thuốc Độc
        repeat(s.activePowers.count { it.id == "P0045" }) {
            if (s.playerBoard.size < 6) {
                s = CombatEngine.summonPlayerUnit(
                    s,
                    CombatEngine.createHandInstance(CardCatalog.getCard("card_caustic_cask")),
                    logs
                ).first
            }
        }

        // P0043: Luật Nhà - Ban 1 trang bị ngẫu nhiên cho 1 lá bài ngẫu nhiên trên tay
        repeat(s.activePowers.count { it.id == "P0043" }) {
            if (s.playerHand.isNotEmpty()) {
                val idx = Random.nextInt(s.playerHand.size)
                val updatedHand = s.playerHand.toMutableList()
                val upgraded = CombatEngine.attachItemToHandCard(updatedHand[idx], CardCatalog.items.random())
                updatedHand[idx] = upgraded
                s = s.copy(playerHand = updatedHand)
                logs.add("Luật Nhà ban trang bị cho ${upgraded.template.name} trên tay!")
            }
        }

        // P1007: Mệnh Lệnh Biệt Kích - Nếu bạn có Lượt Tấn Công, ban Trinh Sát cho 1 đồng minh ngẫu nhiên
        if (s.playerHasAttackToken && s.activePowers.any { it.id == "P1007" }) {
            val nonLandmarks = s.playerBoard.filter { it.template.type != CardType.LANDMARK }
            val candidate = nonLandmarks.filter { !it.keywords.contains(Keyword.SCOUT) }.randomOrNull()
                ?: nonLandmarks.randomOrNull()
            if (candidate != null) {
                s = s.copy(playerBoard = s.playerBoard.map {
                    if (it.instanceId == candidate.instanceId) it.copy(keywords = it.keywords + Keyword.SCOUT) else it
                })
                logs.add("Mệnh Lệnh Biệt Kích ban Trinh Sát cho ${candidate.template.name}.")
            }
        }

        // P0558: Sương Mù Dịch Chuyển - Nếu có Lượt Tấn Công, triệu hồi Màn Sương Xâm Chiếm kèm trang bị
        if (s.playerHasAttackToken) {
            repeat(s.activePowers.count { it.id == "P0558" }) {
                if (s.playerBoard.size < 6) {
                    val mistCard = CombatEngine.attachItemToHandCard(
                        CombatEngine.createHandInstance(CardCatalog.getCard("card_encroaching_mist"), isCreated = true),
                        CardCatalog.items.random()
                    )
                    s = CombatEngine.summonPlayerUnit(s, mistCard, logs).first
                } else {
                    s = CombatEngine.applyEncroachingMistSummonBuff(s, forPlayer = true, logs = logs)
                }
            }
        }

        // P0555: Bản Chất Biến Ảo - Tạo 1 bài phép Phù Du giá gốc <= 6 thuộc khu vực bộ bài với giá 1 tiêu hao
        repeat(s.activePowers.count { it.id == "P0555" }) {
            if (s.playerHand.size < 10) {
                val regionSpells = CardCatalog.cards.values.filter {
                    it.type == CardType.SPELL && it.cost <= 6 &&
                        (s.deckRegions.isEmpty() || it.region in s.deckRegions) &&
                        it.spellSpeed != com.example.model.SpellSpeed.SKILL
                }.ifEmpty {
                    CardCatalog.cards.values.filter {
                        it.type == CardType.SPELL && it.cost <= 6 &&
                            it.spellSpeed != com.example.model.SpellSpeed.SKILL
                    }
                }
                val spellTmpl = regionSpells.random()
                val createdSpell = CombatEngine.createHandInstance(spellTmpl, isFleeting = true, isCreated = true)
                    .copy(effectiveCost = 1)
                s = s.copy(playerHand = s.playerHand + createdSpell)
                logs.add("Bản Chất Biến Ảo tạo ${spellTmpl.name} (1 NL, Phù Du).")
            }
        }

        // P0933: Tinh Tú Hồi Đáp - Tạo Khuếch Tán Tinh Tú lên tay hoặc giảm 2 tiêu hao nếu đã có
        repeat(s.activePowers.count { it.id == "P0933" }) {
            val existingIdx = s.playerHand.indexOfFirst { it.template.id == "card_astral_diffusion" }
            if (existingIdx >= 0) {
                val updatedHand = s.playerHand.toMutableList()
                val targetCard = updatedHand[existingIdx]
                updatedHand[existingIdx] = targetCard.copy(effectiveCost = maxOf(0, targetCard.effectiveCost - 2))
                s = s.copy(playerHand = updatedHand)
                logs.add("Tinh Tú Hồi Đáp giảm 2 tiêu hao cho Khuếch Tán Tinh Tú!")
            } else if (s.playerHand.size < 10) {
                s = s.copy(
                    playerHand = s.playerHand + CombatEngine.createHandInstance(
                        CardCatalog.getCard("card_astral_diffusion"),
                        isCreated = true
                    )
                )
                logs.add("Tinh Tú Hồi Đáp tạo Khuếch Tán Tinh Tú lên tay.")
            }
        }

        // P0232: Cơn Thịnh Nộ Thể Thăng Hoa - Tạo ngẫu nhiên 1 Địa Danh Đếm Ngược Phù Du lên tay
        repeat(s.activePowers.count { it.id == "P0232" }) {
            if (s.playerHand.size < 10) {
                val countdownLandmarks = CardCatalog.cards.values.filter {
                    it.type == CardType.LANDMARK && it.countdownRounds > 0 && it.id != "card_buried_sun_disc"
                }
                if (countdownLandmarks.isNotEmpty()) {
                    val lm = countdownLandmarks.random()
                    s = s.copy(
                        playerHand = s.playerHand + CombatEngine.createHandInstance(lm, isFleeting = true, isCreated = true)
                    )
                    logs.add("Cơn Thịnh Nộ Thể Thăng Hoa tạo ${lm.name} Phù Du lên tay.")
                }
            }
        }

        // P0750: Úm Ba La Biến! - Biến đổi các đơn vị của bạn thành tùy tùng ngẫu nhiên có giá gốc cao hơn 3
        if (s.activePowers.any { it.id == "P0750" }) {
            val unitIdsToTransform = s.playerBoard
                .filter { it.template.type != CardType.LANDMARK }
                .map { it.instanceId }
            if (unitIdsToTransform.isNotEmpty()) {
                unitIdsToTransform.forEach { uid ->
                    val currentUnit = s.playerBoard.find { it.instanceId == uid }
                    if (currentUnit != null) {
                        val candidateTmpl = CombatEngine.findRandomFollowerWithDelta(currentUnit.template.cost + 3)
                        s = CombatEngine.transformPlayerUnitToTemplate(s, uid, candidateTmpl, logs)
                    }
                }
                logs.add("Úm Ba La Biến! biến đổi toàn bộ đơn vị của bạn!")
            }
        }

        // P0119: Game Start token check
        if (isGameStart && s.playerHasAttackToken && s.activePowers.any { it.id == "P0119" }) {
            repeat(s.activePowers.count { it.id == "P0119" }) {
                if (s.playerBoard.size < 6) {
                    s = CombatEngine.summonPlayerUnit(
                        s,
                        CombatEngine.createHandInstance(CardCatalog.getCard("card_sapling")),
                        logs
                    ).first
                }
            }
        }

        return s
    }

    // --- 2. ROUND_END ---

    fun onRoundEndDamageAndKills(state: CombatState, logs: MutableList<String>): CombatState {
        var s = state

        // P0035: Giao Tranh Điên Cuồng - Cuối Vòng Đấu: Gây 2 sát thương lên toàn bộ đồng minh
        val p0035Count = s.activePowers.count { it.id == "P0035" }
        if (p0035Count > 0) {
            val dmg = 2 * p0035Count
            s.playerBoard.filter { it.template.type != CardType.LANDMARK }.map { it.instanceId }.forEach { uid ->
                s = CombatEngine.damageUnit(s, uid, isEnemy = false, damage = dmg, isSpell = false, fromAllyEffect = true, logs = logs)
            }
            logs.add("Giao Tranh Điên Cuồng gây $dmg sát thương lên toàn bộ đồng minh.")
        }

        // P0044: An Tử - Cuối Vòng Đấu: Tiêu diệt TẤT CẢ bài quân đang bị thương
        if (s.activePowers.any { it.id == "P0044" }) {
            val injuredPlayers = s.playerBoard.filter { it.template.type != CardType.LANDMARK && it.health < it.maxHealth }.map { it.instanceId }
            val injuredEnemies = s.enemyBoard.filter { it.template.type != CardType.LANDMARK && it.health < it.maxHealth }.map { it.instanceId }
            injuredPlayers.forEach { id -> s = CombatEngine.removeUnit(s, id, isEnemy = false, logs = logs) }
            injuredEnemies.forEach { id -> s = CombatEngine.removeUnit(s, id, isEnemy = true, logs = logs) }
            if (injuredPlayers.isNotEmpty() || injuredEnemies.isNotEmpty()) {
                logs.add("An Tử tiêu diệt toàn bộ bài quân đang bị thương!")
            }
        }

        return s
    }

    fun shouldKeepRoundBuffs(state: CombatState): Boolean =
        state.activePowers.any { it.id == "P0093" }

    fun onRoundEndPostAdvance(state: CombatState, logs: MutableList<String>): CombatState {
        var s = state

        // P0938: Bàn Cờ Pandora - Cuối Vòng Đấu: Biến đổi mỗi đồng minh thành 1 bài quân ngẫu nhiên có giá gốc cao hơn 2
        if (s.activePowers.any { it.id == "P0938" }) {
            val unitIdsToTransform = s.playerBoard.filter { it.template.type != CardType.LANDMARK }.map { it.instanceId }
            if (unitIdsToTransform.isNotEmpty()) {
                unitIdsToTransform.forEach { uid ->
                    val u = s.playerBoard.find { it.instanceId == uid }
                    if (u != null) {
                        val targetTmpl = CombatEngine.findRandomFollowerWithDelta(u.template.cost + 2)
                        s = CombatEngine.transformPlayerUnitToTemplate(s, uid, targetTmpl, logs)
                    }
                }
                logs.add("Bàn Cờ Pandora biến đổi các đồng minh (+2 tiêu hao)!")
            }
        }

        // P0102: Giảm Giá Chợ Đen - Cuối Vòng Đấu: Giảm 1 tiêu hao của lá bài đắt nhất trên tay bạn
        repeat(s.activePowers.count { it.id == "P0102" }) {
            if (s.playerHand.isNotEmpty()) {
                val maxEntry = s.playerHand.withIndex().maxByOrNull { s.effectivePlayCost(it.value) }
                if (maxEntry != null) {
                    val updatedHand = s.playerHand.toMutableList()
                    val card = maxEntry.value
                    updatedHand[maxEntry.index] = card.copy(effectiveCost = maxOf(0, card.effectiveCost - 1))
                    s = s.copy(playerHand = updatedHand)
                    logs.add("Giảm Giá Chợ Đen giảm 1 tiêu hao cho ${card.template.name}.")
                }
            }
        }

        // P0916: Chiếc Hộp Trang Bị Pandora - Cuối Vòng Đấu: Gắn 1 trang bị Thường ngẫu nhiên cho 1 lá trên tay (tối đa 3)
        repeat(s.activePowers.count { it.id == "P0916" }) {
            val commonItems = CardCatalog.items.filter { it.rarity == Rarity.COMMON }
            val eligibleIndices = s.playerHand.mapIndexedNotNull { idx, hc -> if (hc.attachedItems.size < 3) idx else null }
            if (commonItems.isNotEmpty() && eligibleIndices.isNotEmpty()) {
                val chosenIdx = eligibleIndices.random()
                val updatedHand = s.playerHand.toMutableList()
                val upgraded = CombatEngine.attachItemToHandCard(updatedHand[chosenIdx], commonItems.random())
                updatedHand[chosenIdx] = upgraded
                s = s.copy(playerHand = updatedHand)
                logs.add("Chiếc Hộp Trang Bị Pandora gắn trang bị Thường cho ${upgraded.template.name}!")
            }
        }

        return s
    }

    // --- 3. ON_SUMMON_ALLY & BOARD AURAS ---

    fun applySummonStatModifiers(
        state: CombatState,
        card: HandCardInstance,
        isAllyUnit: Boolean,
        initialPower: Int,
        initialHealth: Int,
        initialKeywords: Set<Keyword>
    ): SummonStatModification {
        var power = initialPower
        var health = initialHealth
        var keywords = initialKeywords
        val tmpl = card.template

        if (isAllyUnit) {
            // P0068: Thiên Tài Của Yipp (1-cost -> +2|+2)
            if (tmpl.cost == 1) {
                val p0068Count = state.activePowers.count { it.id == "P0068" }
                if (p0068Count > 0) {
                    power += 2 * p0068Count
                    health += 2 * p0068Count
                }
            }
            if (card.isCreated) {
                // P0375: Kho Bụi Phép (created ally -> +2|+2)
                val p0375Count = state.activePowers.count { it.id == "P0375" }
                if (p0375Count > 0) {
                    power += 2 * p0375Count
                    health += 2 * p0375Count
                }
                // P0350: Sáu Muỗng Đường (created ally -> +3|+1)
                val p0350Count = state.activePowers.count { it.id == "P0350" }
                if (p0350Count > 0) {
                    power += 3 * p0350Count
                    health += 1 * p0350Count
                }
            }
            // P0914: Cấy Ghép Điều Khiển Học (+1|+1 per attached item, max 3)
            val p0914Count = state.activePowers.count { it.id == "P0914" }
            if (p0914Count > 0) {
                val itemBonus = minOf(3, card.attachedItems.size) * p0914Count
                power += itemBonus
                health += itemBonus
            }
        }

        // P0451: Quảng Trường Vĩ Đại Hơn (+2|+2, Thách Đấu, 1 từ khóa ngẫu nhiên trong vòng này)
        var grandPlazaBonusPow = 0
        var grandPlazaBonusHp = 0
        val grandPlazaAddedKws = mutableSetOf<Keyword>()
        if (isAllyUnit && state.activePowers.any { it.id == "P0451" }) {
            val count = state.activePowers.count { it.id == "P0451" }
            grandPlazaBonusPow = 2 * count
            grandPlazaBonusHp = 2 * count
            power += grandPlazaBonusPow
            health += grandPlazaBonusHp
            if (!keywords.contains(Keyword.CHALLENGER)) {
                keywords = keywords + Keyword.CHALLENGER
                grandPlazaAddedKws.add(Keyword.CHALLENGER)
            }
            val availableKws = Keyword.sharablePool.filterNot { it in keywords }
            val extraKw = (availableKws.ifEmpty { Keyword.sharablePool }).random()
            if (!keywords.contains(extraKw)) {
                keywords = keywords + extraKw
                grandPlazaAddedKws.add(extraKw)
            }
        }

        return SummonStatModification(
            power = power,
            health = health,
            keywords = keywords,
            grandPlazaBonusPow = grandPlazaBonusPow,
            grandPlazaBonusHp = grandPlazaBonusHp,
            grandPlazaAddedKeywords = grandPlazaAddedKws
        )
    }

    fun applyPlayerBoardAuras(unit: UnitOnBoard, state: CombatState): UnitOnBoard {
        if (unit.template.type == CardType.LANDMARK) return unit
        var power = unit.power
        var health = unit.health
        var maxHealth = unit.maxHealth
        var keywords = unit.keywords

        // P0038: Đồng minh có +3|+3, Áp Đảo và Không Thể Chặn Đòn
        val p0038Count = state.activePowers.count { it.id == "P0038" }
        if (p0038Count > 0) {
            power += 3 * p0038Count
            health += 3 * p0038Count
            maxHealth += 3 * p0038Count
            keywords = keywords + Keyword.OVERWHELM + Keyword.CANT_BLOCK
        }
        // P0035: Đồng minh có +2|+2
        val p0035Count = state.activePowers.count { it.id == "P0035" }
        if (p0035Count > 0) {
            power += 2 * p0035Count
            health += 2 * p0035Count
            maxHealth += 2 * p0035Count
        }
        // P0699: Khiên Ánh Dương (Daybreak allies have Tough)
        if (keywords.contains(Keyword.DAYBREAK) && state.activePowers.any { it.id == "P0699" }) {
            keywords = keywords + Keyword.TOUGH
        }
        // Nasus slay aura (+1|+1 per enemy slain this match)
        if (unit.template.id == "card_nasus" && state.playerSlainCount > 0) {
            power += state.playerSlainCount
            health += state.playerSlainCount
            maxHealth += state.playerSlainCount
        }

        return unit.copy(
            power = power,
            health = health,
            maxHealth = maxHealth,
            keywords = keywords
        )
    }

    fun boardAuraStatBonus(unit: UnitOnBoard, state: CombatState): Int {
        val oldTmpl = unit.template
        val oldP0038Count = if (oldTmpl.type != CardType.LANDMARK) state.activePowers.count { it.id == "P0038" } else 0
        val oldP0035Count = if (oldTmpl.type != CardType.LANDMARK) state.activePowers.count { it.id == "P0035" } else 0
        val oldNasusBonus = if (oldTmpl.id == "card_nasus" && !unit.isSilenced) state.playerSlainCount else 0
        return 3 * oldP0038Count + 2 * oldP0035Count + oldNasusBonus
    }

    fun lostToughOnlyFromP0699(
        existing: UnitOnBoard,
        newTemplate: CardTemplate,
        state: CombatState
    ): Boolean =
        existing.template.keywords.contains(Keyword.DAYBREAK) &&
            !newTemplate.keywords.contains(Keyword.DAYBREAK) &&
            state.activePowers.any { it.id == "P0699" } &&
            !(state.championId == "champ_garen" && state.starLevel >= 4) &&
            existing.attachedItems.none { it.grantedKeyword == Keyword.TOUGH } &&
            (existing.equippedGear?.keywords?.contains(Keyword.TOUGH) != true) &&
            existing.supportKeyword != Keyword.TOUGH

    fun shareKeywordsOnSummon(state: CombatState, summonedUnit: UnitOnBoard): List<UnitOnBoard> {
        if (state.activePowers.none { it.id == "P0115" }) return state.playerBoard
        val sharable = summonedUnit.keywords.filter { it.isSharablePositive }.toSet()
        if (sharable.isEmpty()) return state.playerBoard
        return state.playerBoard.map { existing ->
            existing.copy(keywords = existing.keywords + sharable)
        }
    }

    fun onPostAllySummonEffects(
        state: CombatState,
        unitId: String,
        summonedUnit: UnitOnBoard,
        isAllyUnit: Boolean,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        val tmpl = summonedUnit.template

        // P0699: Khi 1 đồng minh kích hoạt Hừng Đông, ban +0|+1 cho toàn bộ đồng minh
        if (isAllyUnit && summonedUnit.keywords.contains(Keyword.DAYBREAK) && s.playerCardsPlayedThisRound == 1) {
            val p0699Count = s.activePowers.count { it.id == "P0699" }
            if (p0699Count > 0) {
                s = s.copy(playerBoard = s.playerBoard.map { u ->
                    if (u.template.type != CardType.LANDMARK) {
                        u.copy(health = u.health + p0699Count, maxHealth = u.maxHealth + p0699Count)
                    } else u
                })
                logs.add("Khiên Ánh Dương ban +0|+$p0699Count cho toàn bộ đồng minh!")
            }
        }

        // P0063: Màn Chào Sân Bùng Nổ - Gây 1 sát thương lên Nhà Chính địch khi triệu hồi đồng minh
        if (isAllyUnit && s.activePowers.any { it.id == "P0063" }) {
            val p0063Count = s.activePowers.count { it.id == "P0063" }
            s = CombatEngine.dealDamageToEnemyNexus(s, p0063Count, logs)
            logs.add("Màn Chào Sân Bùng Nổ gây $p0063Count sát thương lên Nhà Chính địch!")
        }

        // P0232: Cơn Thịnh Nộ Thể Thăng Hoa - Đẩy nhanh Địa Danh Đếm Ngược 2 vòng khi triệu hồi
        if (tmpl.type == CardType.LANDMARK && tmpl.countdownRounds > 0 && s.activePowers.any { it.id == "P0232" }) {
            val advanceAmt = 2 * s.activePowers.count { it.id == "P0232" }
            val currentLm = s.playerBoard.find { it.instanceId == unitId }
            if (currentLm != null) {
                val rem = currentLm.countdownRemaining - advanceAmt
                if (rem <= 0) {
                    s = s.copy(playerBoard = s.playerBoard.filterNot { it.instanceId == unitId })
                    s = CombatEngine.completeCountdownLandmark(s, currentLm, logs)
                } else {
                    s = s.copy(playerBoard = s.playerBoard.map {
                        if (it.instanceId == unitId) it.copy(countdownRemaining = rem) else it
                    })
                    logs.add("Cơn Thịnh Nộ Thể Thăng Hoa đẩy nhanh ${tmpl.name} ($rem vòng còn lại).")
                }
            }
        }

        return s
    }

    // --- 4. ON_PLAY_CARD ---

    fun shouldPayFirstCardWithHealth(state: CombatState): Boolean =
        state.playerCardsPlayedThisRound == 0 && state.activePowers.any { it.id == "P0752" }

    fun onCreatedCardPlayed(
        state: CombatState,
        card: HandCardInstance,
        logs: MutableList<String>
    ): CombatState {
        if (!card.isCreated) return state
        val p0329Count = state.activePowers.count { it.id == "P0329" }
        if (p0329Count <= 0) return state
        logs.add("Ánh Hào Quang Tinh Tú giảm $p0329Count tiêu hao Tướng ở mọi nơi!")
        return state.copy(playerChampionCostReductionEverywhere = state.playerChampionCostReductionEverywhere + p0329Count)
    }

    fun onAfterUnitSummonedFromHand(
        state: CombatState,
        card: HandCardInstance,
        playedUnitId: String?,
        wasNightfall: Boolean,
        logs: MutableList<String>
    ): CombatState {
        var s = state

        // P1010: Nhịp Điệu Tuyệt Vọng (Màn Đêm: Yểm 2 U Ám lên kẻ địch yếu nhất)
        if (wasNightfall &&
            (card.template.type == CardType.UNIT || card.template.type == CardType.CHAMPION) &&
            s.activePowers.any { it.id == "P1010" }
        ) {
            repeat(s.activePowers.count { it.id == "P1010" }) {
                val weakestEnemy = s.enemyBoard
                    .filter { it.template.type != CardType.LANDMARK }
                    .minWithOrNull(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health })
                if (weakestEnemy != null) {
                    s = CombatEngine.applyGloom(s, weakestEnemy.instanceId, isEnemy = true, stacks = 2, isSpell = false, logs = logs)
                }
            }
        }

        // P0897: Thanh Kiếm Darkin khi đánh ra Darkin
        if (playedUnitId != null && card.template.isDarkin && s.activePowers.any { it.id == "P0897" }) {
            s = triggerDarkinStrikePower(s, playedUnitId, logs)
        }

        // P0329: Ánh Hào Quang Tinh Tú - Khi đánh ra 1 Tướng, Thăng Cấp cho Tướng đó
        if (card.template.isChampion && playedUnitId != null && s.activePowers.any { it.id == "P0329" }) {
            s = CombatEngine.levelUpSpecificChampion(s, playedUnitId, logs)
        }

        return s
    }

    fun onPostUnitPlayPowerEffects(
        state: CombatState,
        card: HandCardInstance,
        playedUnitId: String?,
        payWithHealth: Boolean,
        logs: MutableList<String>
    ): Pair<CombatState, Boolean> {
        var s = state

        // P0752: Lời Nguyền Toàn Tri - Sao chép lá bài đầu tiên dùng mỗi vòng
        if (payWithHealth && s.playerBoard.size < 6) {
            val (copyState, copyUnitId) = CombatEngine.summonPlayerUnit(s, card.copy(isCreated = true), logs)
            s = copyState
            if (card.template.isChampion && copyUnitId != null && s.activePowers.any { it.id == "P0329" }) {
                s = CombatEngine.levelUpSpecificChampion(s, copyUnitId, logs)
            }
        }

        // P0385: Dòng Thời Gian Hội Tụ - Lần đầu dùng 1 tùy tùng mỗi vòng, chọn 1 trong 3 tùy tùng có tiêu hao +3
        val timelineTargetId = playedUnitId?.takeIf { uid ->
            card.template.type == CardType.UNIT &&
                s.followersPlayedThisRound == 0 &&
                s.activePowers.any { it.id == "P0385" } &&
                s.playerBoard.any { it.instanceId == uid }
        }

        if (card.template.type == CardType.UNIT) {
            s = s.copy(followersPlayedThisRound = s.followersPlayedThisRound + 1)
        }

        if (timelineTargetId != null) {
            val optionIds = CombatEngine.pickThreeFollowerTransformOptions(card.template.cost + 3)
            s = s.copy(pendingTransformChoice = PendingFollowerTransform(timelineTargetId, optionIds))
            logs.add("Dòng Thời Gian Hội Tụ: Chọn 1 trong 3 tùy tùng để biến đổi ${card.template.name}!")
            return s to true
        }

        return s to false
    }

    fun computeEquipmentP0914Bonus(
        state: CombatState,
        host: UnitOnBoard,
        hadPreviousGear: Boolean
    ): Int {
        val p0914Count = state.activePowers.count { it.id == "P0914" }
        if (p0914Count <= 0) return 0
        val prevP0914Items = minOf(3, host.attachedItems.size + if (hadPreviousGear) 1 else 0)
        val newP0914Items = minOf(3, host.attachedItems.size + 1)
        return (newP0914Items - prevP0914Items) * p0914Count
    }

    fun onEquipmentPlayed(
        state: CombatState,
        card: HandCardInstance,
        hostId: String,
        logs: MutableList<String>
    ): CombatState {
        return if (card.template.isDarkin && state.activePowers.any { it.id == "P0897" }) {
            triggerDarkinStrikePower(state, hostId, logs)
        } else {
            state
        }
    }

    fun onSpellPlayed(
        state: CombatState,
        card: HandCardInstance,
        cost: Int,
        logs: MutableList<String>
    ): CombatState {
        var s = state

        // P1006: Năng Lượng Tái Tạo - Lần đầu dùng bài phép >= 6 tiêu hao mỗi vòng, hồi lại Năng Lượng
        if (card.template.type == CardType.SPELL &&
            cost >= 6 &&
            !s.renewingPowerUsedThisRound &&
            s.activePowers.any { it.id == "P1006" }
        ) {
            val cap = maxOf(s.playerMaxMana, s.playerMana).coerceIn(1, 10)
            s = s.copy(
                playerMana = minOf(cap, s.playerMana + cost),
                renewingPowerUsedThisRound = true
            )
            logs.add("Năng Lượng Tái Tạo hồi lại $cost Năng Lượng!")
        }

        // P0047: Giấy Phép Hội Pháp Sư - Tạo 1 Cầu Vồng Tối Thượng Phù Du khi dùng bài phép khác
        if (card.template.type == CardType.SPELL &&
            card.template.id != "card_final_spark" &&
            s.activePowers.any { it.id == "P0047" } &&
            s.playerHand.size < 10
        ) {
            s = s.copy(
                playerHand = s.playerHand + CombatEngine.createHandInstance(
                    CardCatalog.getCard("card_final_spark"),
                    isFleeting = true,
                    isCreated = true
                )
            )
            logs.add("Giấy Phép Hội Pháp Sư tạo 1 Cầu Vồng Tối Thượng lên tay!")
        }

        // P0997: Mề Đay Hồi Sinh - Khi dùng Đổi Thế Võ, hồi 2 Máu Nhà Chính và 1 Năng Lượng Phép
        if ((card.template.id == "card_stance_swap" || card.template.effectType == CardEffectType.STANCE_SWAP_EFFECT) &&
            s.activePowers.any { it.id == "P0997" }
        ) {
            s = s.copy(
                playerNexusHp = minOf(s.playerMaxHp, s.playerNexusHp + 2),
                playerSpellMana = minOf(3, s.playerSpellMana + 1)
            )
            logs.add("Mề Đay Hồi Sinh hồi 2 Máu Nhà Chính và 1 Năng Lượng Phép!")
        }

        // P0888: Bài Học Thứ Hai - Hoạt hóa bài phép chỉ nhắm vào đồng minh
        if (card.template.type == CardType.SPELL &&
            s.activePowers.any { it.id == "P0888" } &&
            CombatEngine.isAllyOnlySpell(s, card.template) &&
            s.playerBoard.size < 6
        ) {
            val stat = maxOf(1, card.template.cost)
            val animatedTmpl = CardCatalog.getCard("card_animated_spell")
            val animatedCard = CombatEngine.createHandInstance(animatedTmpl, isCreated = true).copy(
                bonusPower = maxOf(0, stat - animatedTmpl.basePower),
                bonusHealth = maxOf(0, stat - animatedTmpl.baseHealth)
            )
            s = CombatEngine.summonPlayerUnit(s, animatedCard, logs).first
            logs.add("Bài Học Thứ Hai hoạt hóa ${card.template.name} thành quân $stat|$stat!")
        }

        return s
    }

    fun resolveSingleTargetSpellShareId(
        state: CombatState,
        card: HandCardInstance,
        targetUnitId: String?
    ): String? {
        if (card.template.type != CardType.SPELL ||
            CombatEngine.requiredPlayerTargetCount(card.template) != 1 ||
            targetUnitId == null ||
            state.playerBoard.none { it.instanceId == targetUnitId && it.template.type != CardType.LANDMARK } ||
            state.activePowers.none { it.id == "P0118" }
        ) {
            return null
        }
        return state.playerBoard
            .filter { it.instanceId != targetUnitId && it.template.type != CardType.LANDMARK }
            .minWithOrNull(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health })
            ?.instanceId
    }

    // --- 5. ON_ALLY_DEATH ---

    fun onAllyDeath(
        state: CombatState,
        deadUnit: UnitOnBoard,
        logs: MutableList<String>
    ): CombatState {
        var s = state

        // P0378: Kế Thừa - Ban Sức Mạnh của đồng minh hy sinh cho 1 bài quân trên tay
        repeat(s.activePowers.count { it.id == "P0378" }) {
            val allyPow = maxOf(0, deadUnit.effectivePower)
            if (allyPow > 0) {
                val unitIndices = s.playerHand.mapIndexedNotNull { i, hc ->
                    if (hc.template.type == CardType.UNIT || hc.template.type == CardType.CHAMPION) i else null
                }
                if (unitIndices.isNotEmpty()) {
                    val chosenIdx = unitIndices.random()
                    val updatedHand = s.playerHand.toMutableList()
                    val chosenCard = updatedHand[chosenIdx]
                    updatedHand[chosenIdx] = chosenCard.copy(bonusPower = chosenCard.bonusPower + allyPow)
                    s = s.copy(playerHand = updatedHand)
                    logs.add("Kế Thừa ban +$allyPow|+0 cho ${chosenCard.template.name} trên tay!")
                }
            }
        }

        // P1011: Cái Giá Của Sự Trả Thù - Gây 1 sát thương lên Nhà Chính địch
        val p1011Count = s.activePowers.count { it.id == "P1011" }
        if (p1011Count > 0) {
            s = CombatEngine.dealDamageToEnemyNexus(s, p1011Count, logs)
            logs.add("Cái Giá Của Sự Trả Thù gây $p1011Count sát thương lên Nhà Chính địch!")
        }

        return s
    }

    // --- 6. OTHER POWER HOOKS ---

    fun onEnemyDamagedPowerReduction(state: CombatState, currentPower: Int, actualDamage: Int): Int {
        return if (state.activePowers.any { it.id == "pow_enfeebling_strike" }) {
            maxOf(0, currentPower - actualDamage)
        } else {
            currentPower
        }
    }

    fun onEnemyNexusDamaged(state: CombatState): CombatState {
        if (state.activePowers.none { it.id == "pow_raiding_party" } || state.playerHand.isEmpty()) return state
        val firstUnitIdx = state.playerHand.indexOfFirst {
            it.template.type == CardType.UNIT || it.template.type == CardType.CHAMPION
        }
        if (firstUnitIdx < 0) return state
        val updatedHand = state.playerHand.toMutableList()
        val card = updatedHand[firstUnitIdx]
        updatedHand[firstUnitIdx] = card.copy(bonusPower = card.bonusPower + 1, bonusHealth = card.bonusHealth + 1)
        return state.copy(playerHand = updatedHand)
    }

    fun hasNexusToughPower(state: CombatState): Boolean =
        state.activePowers.any { it.id == "P0549" }

    fun onChampionLevelUp(state: CombatState, logs: MutableList<String>): CombatState {
        var s = state
        if (s.activePowers.any { it.id == "P0671" } && s.playerBoard.size < 6) {
            val summonId = when {
                s.playerBoard.none { it.template.id == "card_lux" } -> "card_lux"
                s.playerBoard.none { it.template.id == "card_garen" } -> "card_garen"
                s.playerBoard.none { it.template.id == "card_galio" } -> "card_galio"
                else -> null
            }
            if (summonId != null) {
                s = CombatEngine.summonPlayerUnit(
                    s,
                    CombatEngine.createHandInstance(CardCatalog.getCard(summonId)),
                    logs
                ).first
                logs.add("Những Người Bảo Hộ Demacia triệu hồi ${CardCatalog.getCard(summonId).name}!")
            }
        }
        return s
    }

    fun onAttackTokenGained(state: CombatState, logs: MutableList<String>): CombatState {
        var s = state
        repeat(s.activePowers.count { it.id == "P0119" }) {
            if (s.playerBoard.size < 6) {
                s = CombatEngine.summonPlayerUnit(
                    s,
                    CombatEngine.createHandInstance(CardCatalog.getCard("card_sapling")),
                    logs
                ).first
                logs.add("Sự Báo Thù Của Thiên Nhiên triệu hồi 1 Chồi Non (2|1)!")
            }
        }
        return s
    }

    fun onEnemyCreatedCard(
        state: CombatState,
        card: CardTemplate,
        logs: MutableList<String>
    ): Pair<CombatState, Boolean> {
        if (!state.stoleEnemyCreatedCardThisRound && state.activePowers.any { it.id == "P0756" }) {
            val stolenHand = if (state.playerHand.size < 10) {
                state.playerHand + CombatEngine.createHandInstance(card, isFleeting = true, isCreated = true)
                    .copy(effectiveCost = 0)
            } else {
                state.playerHand
            }
            logs.add("Cấm Gian Lận: Đánh cắp ${card.name} của đối thủ với giá 0 tiêu hao và Phù Du!")
            return state.copy(
                playerHand = stolenHand,
                stoleEnemyCreatedCardThisRound = true
            ) to true
        }
        return state to false
    }

    fun onTrapTriggeredOnEnemyDraw(
        state: CombatState,
        drawnCard: CardTemplate,
        trapsTriggered: Int,
        logs: MutableList<String>
    ): CombatState {
        if (state.activePowers.any { it.id == "P0642" } && state.playerHand.size < 10) {
            val reducedCost = maxOf(0, drawnCard.cost - 2 * trapsTriggered)
            val stolenCopy = CombatEngine.createHandInstance(drawnCard, isFleeting = true, isCreated = true).copy(
                effectiveCost = reducedCost
            )
            logs.add("Ta Lấy Cả Cái Đó: Tạo bản sao ${drawnCard.name} ($reducedCost NL, Phù Du) lên tay!")
            return state.copy(playerHand = state.playerHand + stolenCopy)
        }
        return state
    }

    fun triggerDarkinStrikePower(
        state: CombatState,
        darkinUnitId: String,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        val repeatCount = s.activePowers.count { it.id == "P0897" }
        repeat(repeatCount) {
            val weakest = s.enemyBoard
                .filter { it.template.type != CardType.LANDMARK }
                .minWithOrNull(compareBy<UnitOnBoard> { it.health }.thenBy { it.effectivePower })
            val darkinUnit = s.playerBoard.find { it.instanceId == darkinUnitId }
            if (darkinUnit != null && weakest != null) {
                s = CombatEngine.performNonCombatStrike(
                    state = s,
                    strikerId = darkinUnit.instanceId,
                    strikerIsPlayer = true,
                    targetId = weakest.instanceId,
                    targetIsEnemy = true,
                    logs = logs
                )
                logs.add("Thanh Kiếm Darkin: ${darkinUnit.template.name} ra đòn vào ${weakest.template.name}!")
            }
        }
        return s
    }

    fun onTransformToDarkin(
        state: CombatState,
        newTemplate: CardTemplate,
        unitInstanceId: String,
        logs: MutableList<String>
    ): CombatState {
        return if (newTemplate.isDarkin && state.activePowers.any { it.id == "P0897" }) {
            triggerDarkinStrikePower(state, unitInstanceId, logs)
        } else {
            state
        }
    }
}
