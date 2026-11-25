package com.example

import com.example.data.local.dao.GameDao
import com.example.data.local.entity.ChampionLoadoutEntity
import com.example.data.local.entity.PlayerMetaEntity
import com.example.data.local.entity.SavedRunEntity
import com.example.data.repository.GameRepository
import com.example.engine.CombatEngine
import com.example.engine.CombatPhase
import com.example.engine.CombatState
import com.example.engine.PriorityHolder
import com.example.model.AdventureCatalog
import com.example.model.CardCatalog
import com.example.model.CardTemplate
import com.example.model.CardType
import com.example.model.ChampionLoadout
import com.example.model.DeckCardEntry
import com.example.model.HandCardInstance
import com.example.model.Keyword
import com.example.model.PowerTemplate
import com.example.model.Rarity
import com.example.model.Region
import com.example.model.RunState
import com.example.model.UnitOnBoard
import com.example.viewmodel.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class EpicPowersFakeGameDao : GameDao {
    private val loadouts = MutableStateFlow<List<ChampionLoadoutEntity>>(emptyList())
    private val meta = MutableStateFlow<PlayerMetaEntity?>(null)
    private val savedRun = MutableStateFlow<SavedRunEntity?>(null)

    override fun observeChampionLoadouts(): Flow<List<ChampionLoadoutEntity>> = loadouts
    override suspend fun getChampionLoadout(id: String): ChampionLoadoutEntity? =
        loadouts.value.find { it.championId == id }

    override suspend fun upsertChampionLoadout(entity: ChampionLoadoutEntity) {
        loadouts.value = loadouts.value.filterNot { it.championId == entity.championId } + entity
    }

    override suspend fun upsertAllChampionLoadouts(entities: List<ChampionLoadoutEntity>) {
        val ids = entities.map { it.championId }.toSet()
        loadouts.value = loadouts.value.filterNot { ids.contains(it.championId) } + entities
    }

    override fun observePlayerMeta(): Flow<PlayerMetaEntity?> = meta
    override suspend fun getPlayerMeta(): PlayerMetaEntity? = meta.value
    override suspend fun upsertPlayerMeta(entity: PlayerMetaEntity) {
        meta.value = entity
    }

    override suspend fun deleteOtherChampionLoadouts(keepId: String) {
        loadouts.value = loadouts.value.filter { it.championId == keepId }
    }

    override fun observeSavedRun(): Flow<SavedRunEntity?> = savedRun
    override suspend fun getSavedRun(): SavedRunEntity? = savedRun.value
    override suspend fun upsertSavedRun(entity: SavedRunEntity) {
        savedRun.value = entity
    }

    override suspend fun clearSavedRun() {
        savedRun.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EpicPowersUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun baseEpicPowerTestState(
        powerIds: List<String>,
        playerHand: List<HandCardInstance> = emptyList(),
        playerDrawPile: List<HandCardInstance> = emptyList(),
        playerBoard: List<UnitOnBoard> = emptyList(),
        enemyBoard: List<UnitOnBoard> = emptyList(),
        enemyHand: List<CardTemplate> = emptyList(),
        enemyDrawPool: List<CardTemplate> = emptyList(),
        playerMana: Int = 10,
        playerMaxMana: Int = 10,
        playerSpellMana: Int = 0,
        playerHasAttackToken: Boolean = true,
        startingDeckSize: Int = 20,
        deckRegions: Set<Region> = setOf(Region.DEMACIA, Region.PILTOVER_ZAUN)
    ): CombatState {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        return CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_lux",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = powerIds.map { CardCatalog.getPower(it) },
            mutators = emptyList(),
            playerNexusHp = 40,
            playerMaxHp = 40,
            enemyNexusHp = 30,
            enemyMaxHp = 30,
            playerMana = playerMana,
            playerMaxMana = playerMaxMana,
            playerSpellMana = playerSpellMana,
            playerHasAttackToken = playerHasAttackToken,
            playerHand = playerHand,
            playerDrawPile = playerDrawPile,
            playerBoard = playerBoard,
            enemyBoard = enemyBoard,
            enemyHand = enemyHand,
            enemyDrawPool = enemyDrawPool,
            startingDeckSize = startingDeckSize,
            deckRegions = deckRegions,
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )
    }

    @Test
    fun `epic power catalog has 55 new powers removes duplicates and sanitizes saved run`() = runTest(testDispatcher) {
        val epics = CardCatalog.powers.filter { it.rarity == Rarity.EPIC }
        assertEquals(55, epics.size)
        val expectedIds = setOf(
            "P0067", "P0101", "P0057", "P0226", "P1115", "P1122", "P0073",
            "P0112", "P0064", "P0050", "P0109", "P0045", "P0043", "P1007", "P0558", "P0555", "P0933", "P0232", "P0750", "P0119",
            "P0102", "P0916", "P0938", "P0044",
            "P0115", "P0063", "P0068", "P0375", "P0350", "P0451", "P0385", "P0897", "P1010",
            "P0326", "P0118", "P0888", "P1006", "P0047", "P0997", "P0329", "P0752", "P0756", "P0642",
            "P0378", "P1011", "P0671", "P0093", "P0549", "P0038", "P0035", "P0048", "P0680", "P0222", "P0914", "P0699"
        )
        assertEquals(expectedIds, epics.map { it.id }.toSet())
        listOf("pow_evolution", "pow_trifarian_might", "pow_sharing_is_caring", "pow_stabilize", "pow_domination").forEach { oldId ->
            assertNull(CardCatalog.findPowerOrNull(oldId))
        }
        val manaflow = CardCatalog.findPowerOrNull("pow_manaflow")
        assertNotNull(manaflow)
        assertEquals(Rarity.LEGENDARY, manaflow!!.rarity)

        // Verify GameRepository migrates pow_domination -> P0112 and strips obsolete IDs
        val dao = EpicPowersFakeGameDao()
        val repo = GameRepository(dao, testDispatcher)
        val staleRun = RunState(
            runId = "run_stale",
            championId = "champ_anivia",
            adventureId = "adv_demacia_outskirts",
            currentNodeId = "d_fight_1a",
            completedNodeIds = emptyList(),
            currentNexusHp = 30,
            maxNexusHp = 30,
            gold = 100,
            rerollsRemaining = 2,
            revivesRemaining = 1,
            activePowerIds = listOf("pow_evolution", "pow_domination", "P0067", "pow_stabilize"),
            runDeck = emptyList()
        )
        repo.saveActiveRun(staleRun)
        repo.ensureSeeded()
        val sanitized = repo.activeSavedRun.first()
        assertNotNull(sanitized)
        assertEquals(listOf("P0112", "P0067"), sanitized!!.activePowerIds)
    }

    @Test
    fun `power reward fallback picks nearest rarity instead of skipping straight to random pool`() = runTest(testDispatcher) {
        val dao = EpicPowersFakeGameDao()
        val repo = GameRepository(dao, testDispatcher)
        val vm = GameViewModel(repo)

        val common = PowerTemplate("t_c", "Common", Rarity.COMMON, "")
        val epic = PowerTemplate("t_e", "Epic", Rarity.EPIC, "")
        val legendary = PowerTemplate("t_l", "Legendary", Rarity.LEGENDARY, "")

        // Rolling RARE when RARE bucket is empty falls back to COMMON before EPIC/LEGENDARY
        val rareFallback = vm.pickPowerForRarityWithFallback(listOf(epic, legendary, common), Rarity.RARE)
        assertEquals("t_c", rareFallback?.id)

        // Rolling LEGENDARY when LEGENDARY bucket is empty falls back to EPIC before RARE/COMMON
        val legendaryFallback = vm.pickPowerForRarityWithFallback(listOf(common, epic), Rarity.LEGENDARY)
        assertEquals("t_e", legendaryFallback?.id)

        // Rolling EPIC when EPIC bucket is empty falls back to RARE before LEGENDARY/COMMON
        val rare = PowerTemplate("t_r", "Rare", Rarity.RARE, "")
        val epicFallback = vm.pickPowerForRarityWithFallback(listOf(common, rare, legendary), Rarity.EPIC)
        assertEquals("t_r", epicFallback?.id)
    }

    @Test
    fun `epic powers group 1 game start and gold victory mechanics work`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val twoDrop = CardCatalog.cards.values.first { it.type == CardType.UNIT && it.cost == 2 }
        val run = RunState(
            runId = "run_g1",
            championId = "champ_lux",
            adventureId = adv.id,
            currentNodeId = node.id,
            completedNodeIds = emptyList(),
            currentNexusHp = 30,
            maxNexusHp = 30,
            gold = 0,
            rerollsRemaining = 2,
            revivesRemaining = 1,
            activePowerIds = listOf("P0067", "P0101", "P0057", "P0226", "P1115", "P1122", "P0073"),
            runDeck = listOf(DeckCardEntry(cardId = twoDrop.id, copies = 8))
        )
        val loadout = ChampionLoadout("champ_lux", true, 1, 1)
        val combat = CombatEngine.createEncounter(run, loadout, adv, node)

        // P0067: +1 starting mana crystal (base 1 -> 2)
        assertTrue(combat.playerMaxMana >= 2)
        // P0101: summoned 2-cost unit from deck
        assertTrue(combat.playerBoard.any { it.template.id == twoDrop.id })
        // P0057: summoned Hextech Observatory
        assertTrue(combat.playerBoard.any { it.template.id == "card_hextech_observatory" })
        // P0226: summoned Buried Sun Disc and created Azir/Renekton/Nasus in hand
        assertTrue(combat.playerBoard.any { it.template.id == "card_buried_sun_disc" })
        assertTrue(combat.playerHand.any { it.template.id in setOf("card_renekton", "card_azir", "card_nasus") })
        // P1115: created Forged Blade (Darkin) with attached item in hand
        val darkinInHand = combat.playerHand.find { it.template.id == "card_forged_blade" }
        assertNotNull(darkinInHand)
        assertTrue(darkinInHand!!.attachedItems.isNotEmpty())
        // P1122: created Breakout in hand
        assertTrue(combat.playerHand.any { it.template.id == "card_breakout" })
        // P0073: metadata confirms +200 gold victory power is registered and active
        assertTrue(combat.activePowers.any { it.id == "P0073" })
    }

    @Test
    fun `epic powers group 2 round start and attack token gained mechanics work`() {
        val enemyStrong = UnitOnBoard("e_s", CardCatalog.getCard("card_badgerbear"), false, 6, 5, 5, emptySet())
        val enemyWeak = UnitOnBoard("e_w", CardCatalog.getCard("card_spiderling"), false, 1, 1, 1, emptySet())
        val handCard = HandCardInstance(instanceId = "h1", template = CardCatalog.getCard("card_cithria"), effectiveCost = 1)
        val playerWeak = UnitOnBoard("p_w", CardCatalog.getCard("card_cithria"), true, 2, 2, 2, emptySet())

        // P0112 (Rally), P0064 (Stun strongest enemy), P0050 (Stun strongest + weakest), P0109 (1-cost Poro), P0045 (Caustic Cask)
        val s1 = CombatEngine.applyRoundStartHooks(
            baseEpicPowerTestState(
                powerIds = listOf("P0112", "P0064", "P0050", "P0109", "P0045", "P0043", "P1007", "P0558", "P0555", "P0933", "P0232"),
                playerHand = listOf(handCard),
                playerBoard = listOf(playerWeak),
                enemyBoard = listOf(enemyStrong, enemyWeak),
                playerHasAttackToken = false
            ).copy(round = 2),
            isGameStart = false
        )
        assertTrue(s1.playerHasAttackToken)
        assertTrue(s1.enemyBoard.first { it.instanceId == "e_s" }.isStunned)
        assertTrue(s1.enemyBoard.first { it.instanceId == "e_w" }.isStunned)
        assertTrue(s1.playerBoard.any { it.template.isPoro })
        assertTrue(s1.playerBoard.any { it.template.id == "card_caustic_cask" })
        // P0043 gave an item to handCard
        assertTrue(s1.playerHand.first { it.instanceId == "h1" }.attachedItems.isNotEmpty())
        // P1007 gave Scout to an ally
        assertTrue(s1.playerBoard.any { it.keywords.contains(Keyword.SCOUT) })
        // P0558 summoned Encroaching Mist with item & buffed it
        val mist = s1.playerBoard.find { it.template.id == "card_encroaching_mist" }
        assertNotNull(mist)
        assertTrue(mist!!.attachedItems.isNotEmpty() || mist.power >= 2)
        // P0555 created fleeting 1-cost spell from deck region
        assertTrue(s1.playerHand.any { it.isFleeting && it.effectiveCost == 1 && it.template.type == CardType.SPELL })
        // P0933 created Astral Diffusion; second RoundStart reduces its cost by 2
        val astral = s1.playerHand.find { it.template.id == "card_astral_diffusion" }!!
        val s1NextRound = CombatEngine.applyRoundStartHooks(s1.copy(round = 3), isGameStart = false)
        val discountedAstral = s1NextRound.playerHand.find { it.instanceId == astral.instanceId }
        assertNotNull(discountedAstral)
        assertEquals(maxOf(0, astral.effectiveCost - 2), discountedAstral!!.effectiveCost)
        // P0232 creates fleeting countdown landmark + advances summoned countdown landmarks by 2
        assertTrue(s1.playerHand.any { it.isFleeting && it.template.type == CardType.LANDMARK && it.template.countdownRounds > 0 })

        val sCountdown = baseEpicPowerTestState(
            powerIds = listOf("P0232"),
            playerHand = listOf(HandCardInstance(instanceId = "lm_rock", template = CardCatalog.getCard("card_hibernating_rockbear"), effectiveCost = 2))
        )
        // P0232 advances countdown 3 -> 1 immediately on summon, then next RoundStart ticks 1 -> 0 and hatches Grumpy Rockbear
        val afterRock = CombatEngine.playCardFromHand(sCountdown, "lm_rock")
        assertEquals(1, afterRock.playerBoard.single().countdownRemaining)
        val afterRockHatch = CombatEngine.advanceToNextRound(afterRock, mutableListOf())
        assertTrue(afterRockHatch.playerBoard.any { it.template.id == "card_grumpy_rockbear" })

        // P0750 transforms allies into follower costing 3 more at RoundStart
        val s750 = CombatEngine.applyRoundStartHooks(
            baseEpicPowerTestState(
                powerIds = listOf("P0750"),
                playerBoard = listOf(UnitOnBoard("u1", CardCatalog.getCard("card_cithria"), true, 2, 2, 2, emptySet()))
            ).copy(round = 2),
            isGameStart = false
        )
        assertTrue(s750.playerBoard.single().template.cost >= 4)

        // P0119 summons Sapling on gaining attack token (Round 2 -> Round 3 gives player attack token)
        val s119 = CombatEngine.advanceToNextRound(
            baseEpicPowerTestState(powerIds = listOf("P0119"), playerHasAttackToken = false).copy(round = 2),
            mutableListOf()
        )
        assertTrue(s119.playerBoard.any { it.template.id == "card_sapling" })
    }

    @Test
    fun `epic powers group 3 round end mechanics work`() {
        val expensive = HandCardInstance(instanceId = "exp", template = CardCatalog.getCard("card_tiana_crownguard"), effectiveCost = 8)
        val cheap = HandCardInstance(instanceId = "chp", template = CardCatalog.getCard("card_cithria"), effectiveCost = 1)
        val wounded = UnitOnBoard("wnd", CardCatalog.getCard("card_badgerbear"), true, 4, 2, 4, emptySet())
        val healthy = UnitOnBoard("hlt", CardCatalog.getCard("card_cithria"), true, 2, 2, 2, emptySet())

        val afterP0102 = CombatEngine.advanceToNextRound(
            baseEpicPowerTestState(
                powerIds = listOf("P0102"),
                playerHand = listOf(expensive, cheap)
            ),
            mutableListOf()
        )
        // P0102 reduced expensive card cost by 1
        assertEquals(7, afterP0102.playerHand.first { it.instanceId == "exp" }.effectiveCost)

        val afterEnd = CombatEngine.advanceToNextRound(
            baseEpicPowerTestState(
                powerIds = listOf("P0916", "P0938", "P0044"),
                playerHand = listOf(expensive, cheap),
                playerBoard = listOf(wounded, healthy)
            ),
            mutableListOf()
        )
        // P0916 attached a common item to a hand card
        assertTrue(afterEnd.playerHand.any { it.attachedItems.isNotEmpty() })
        // P0044 killed wounded unit before P0938 transformed surviving healthy ally
        assertEquals(1, afterEnd.playerBoard.size)
        // P0938 transformed surviving 1-cost healthy unit into follower costing 2 more
        assertTrue(afterEnd.playerBoard.single().template.cost >= 3)
    }

    @Test
    fun `epic powers group 4 summon and play unit mechanics work`() {
        val existingAlly = UnitOnBoard("ally_0", CardCatalog.getCard("card_cithria"), true, 2, 2, 2, emptySet())
        val oneDrop = HandCardInstance(instanceId = "h_1", template = CardCatalog.getCard("card_cithria"), effectiveCost = 1)
        val createdTwoDrop = HandCardInstance(instanceId = "h_c", template = CardCatalog.getCard("card_fleetfeather_tracker"), effectiveCost = 1, isCreated = true)
        val darkinHand = HandCardInstance(instanceId = "h_dk", template = CardCatalog.getCard("card_taarosh"), effectiveCost = 8)
        val enemyWeak = UnitOnBoard("e_1", CardCatalog.getCard("card_spiderling"), false, 1, 2, 2, emptySet())

        // Test P0115 (share keywords), P0063 (1 nexus dmg), P0068 (1-cost +2|+2), P0451 (+2|+2, Challenger, random kw)
        val s1 = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0115", "P0063", "P0068", "P0451"),
                playerHand = listOf(oneDrop),
                playerBoard = listOf(existingAlly)
            ),
            "h_1"
        )
        assertEquals(29, s1.enemyNexusHp) // P0063 dealt 1 damage
        val summoned = s1.playerBoard.last()
        assertTrue(summoned.keywords.contains(Keyword.CHALLENGER))
        assertTrue(summoned.power >= 6) // 2 + 2 (Yipp) + 2 (Grand Plaza)
        assertTrue(s1.playerBoard.first { it.instanceId == "ally_0" }.keywords.contains(Keyword.CHALLENGER))

        // Test P0375 (+2|+2 created) & P0350 (+3|+1 created)
        val sCreated = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0375", "P0350"),
                playerHand = listOf(createdTwoDrop)
            ),
            "h_c"
        )
        val cUnit = sCreated.playerBoard.single()
        assertEquals(7, cUnit.power) // 2 + 2 + 3
        assertEquals(4, cUnit.health) // 1 + 2 + 1

        // Test P0385 (Concurrent Timelines prompt & choice)
        val follower = HandCardInstance(instanceId = "h_flw", template = CardCatalog.getCard("card_cithria"), effectiveCost = 1)
        val s385 = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0385"),
                playerHand = listOf(follower)
            ),
            "h_flw"
        )
        assertNotNull(s385.pendingTransformChoice)
        assertEquals(3, s385.pendingTransformChoice!!.optionCardIds.size)
        val chosenId = s385.pendingTransformChoice.optionCardIds.first()
        val s385Chosen = CombatEngine.resolveFollowerTransformChoice(s385, chosenId)
        assertNull(s385Chosen.pendingTransformChoice)
        assertEquals(chosenId, s385Chosen.playerBoard.single().template.id)

        // Test P0897 (Darkin unit + Darkin equipment strike weakest enemy) & P1010 (Nightfall Gloom 2)
        val sDarkin = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0897", "P1010"),
                playerHand = listOf(follower, darkinHand),
                enemyBoard = listOf(enemyWeak)
            ),
            "h_flw"
        ).copy(priorityHolder = PriorityHolder.PLAYER)
        // Playing first card did not trigger Nightfall; playing second card triggers P1010 (-2|-2 -> kills 1|2 enemy)
        val sNightfall = CombatEngine.playCardFromHand(sDarkin, "h_dk")
        assertTrue(sNightfall.enemyBoard.isEmpty())

        val eqTarget = UnitOnBoard("eq_u", CardCatalog.getCard("card_cithria"), true, 3, 4, 4, emptySet())
        val darkinEquip = HandCardInstance(instanceId = "h_blade", template = CardCatalog.getCard("card_forged_blade"), effectiveCost = 2)
        val toughEnemy = UnitOnBoard("e_t", CardCatalog.getCard("card_badgerbear"), false, 4, 4, 4, emptySet())
        val sEquipDarkin = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0897"),
                playerHand = listOf(darkinEquip),
                playerBoard = listOf(eqTarget),
                enemyBoard = listOf(toughEnemy)
            ),
            "h_blade",
            targetUnitId = "eq_u"
        )
        // Equipped ally gained +2|+1 -> 5 power, struck 4-HP enemy and killed it
        assertTrue(sEquipDarkin.enemyBoard.isEmpty())
    }

    @Test
    fun `epic powers group 5 spell created card and trap mechanics work`() {
        val ally1 = UnitOnBoard("a1", CardCatalog.getCard("card_cithria"), true, 2, 2, 2, emptySet())
        val ally2 = UnitOnBoard("a2", CardCatalog.getCard("card_fleetfeather_tracker"), true, 1, 1, 1, emptySet())
        val enemy = UnitOnBoard("e1", CardCatalog.getCard("card_badgerbear"), false, 4, 5, 5, emptySet())
        val dmgSpell = HandCardInstance(instanceId = "sp_dmg", template = CardCatalog.getCard("card_mystic_shot"), effectiveCost = 2) // 2 base dmg
        val buffSpell = HandCardInstance(instanceId = "sp_buf", template = CardCatalog.getCard("card_prismatic_barrier"), effectiveCost = 2)
        val bigSpell = HandCardInstance(instanceId = "sp_big", template = CardCatalog.getCard("card_clash_of_giants"), effectiveCost = 7)
        val stanceSpell = HandCardInstance(instanceId = "sp_st", template = CardCatalog.getCard("card_stance_swap"), effectiveCost = 3)

        // P0326 (+2 spell/skill dmg), P0047 (spells fleeting + Final Spark), P0118 (copy ally single-target spell), P0888 (animate ally spell)
        val sSpell = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0326", "P0047", "P0118", "P0888"),
                playerHand = listOf(dmgSpell, buffSpell),
                playerBoard = listOf(ally1, ally2),
                enemyBoard = listOf(enemy)
            ),
            "sp_dmg",
            targetUnitId = "e1"
        )
        assertTrue(sSpell.effectiveIsFleeting(sSpell.playerHand.first { it.instanceId == "sp_buf" }))
        // Mystic Shot 2 + 2 = 4 dmg -> enemy 5 HP drops to 1
        val afterStack = CombatEngine.resolveSpellStack(sSpell).copy(priorityHolder = PriorityHolder.PLAYER)
        assertEquals(1, afterStack.enemyBoard.single().health)
        assertTrue(afterStack.playerHand.any { it.template.id == "card_final_spark" })

        val afterBuff = CombatEngine.playCardFromHand(afterStack, "sp_buf", targetUnitId = "a1")
        // P0118 copied Barrier to weakest other ally (a2) and P0888 summoned a 1|1 Animated Spell
        assertTrue(afterBuff.playerBoard.first { it.instanceId == "a1" }.hasBarrier)
        assertTrue(afterBuff.playerBoard.first { it.instanceId == "a2" }.hasBarrier)
        assertTrue(afterBuff.playerBoard.any { it.template.id == "card_animated_spell" })

        // P1006 refunds mana for first 6+ cost spell
        val s1006 = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(powerIds = listOf("P1006"), playerHand = listOf(bigSpell), playerMana = 8, playerMaxMana = 8),
            "sp_big"
        )
        assertEquals(8, s1006.playerMana)

        // P0997 heals 2 nexus and refills 1 spell mana when playing Stance Swap
        val s997 = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(powerIds = listOf("P0997"), playerHand = listOf(stanceSpell), playerMana = 5).copy(playerNexusHp = 30),
            "sp_st",
            targetUnitId = "a1"
        )
        assertEquals(32, s997.playerNexusHp)
        assertEquals(1, s997.playerSpellMana)

        // P0329 created card reduces champ cost everywhere & playing champ auto-levels it
        val createdCard = HandCardInstance(instanceId = "hc_cr", template = CardCatalog.getCard("card_prismatic_barrier"), effectiveCost = 2, isCreated = true)
        val champInHand = HandCardInstance(instanceId = "hc_lx", template = CardCatalog.getCard("card_lux"), effectiveCost = 5)
        val s329 = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0329"),
                playerHand = listOf(createdCard, champInHand),
                playerBoard = listOf(ally1)
            ),
            "hc_cr",
            targetUnitId = "a1"
        )
        assertEquals(4, s329.effectivePlayCost(s329.playerHand.first { it.instanceId == "hc_lx" }))
        val s329Champ = CombatEngine.playCardFromHand(s329, "hc_lx")
        assertTrue(s329Champ.playerBoard.any { it.template.id == "card_lux" && it.isLeveledUp })

        // P0752 copies first card each round and pays with Nexus Health
        val s752 = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0752"),
                playerHand = listOf(HandCardInstance(instanceId = "h_payhp", template = CardCatalog.getCard("card_tiana_crownguard"), effectiveCost = 8)),
                playerMana = 0,
                playerMaxMana = 1
            ),
            "h_payhp"
        )
        assertEquals(32, s752.playerNexusHp)
        assertEquals(2, s752.playerBoard.size)

        // P0756 steals first enemy created card at cost 0 Fleeting
        val s756 = CombatEngine.addEnemyCreatedCardToHand(
            baseEpicPowerTestState(powerIds = listOf("P0756")),
            CardCatalog.getCard("card_hex_core_upgrade"),
            mutableListOf()
        )
        assertTrue(s756.playerHand.any { it.template.id == "card_hex_core_upgrade" && it.effectiveCost == 0 && it.isFleeting })

        // P0642 copies trapped drawn enemy card with 2*traps discount and Fleeting
        val enemyTop = CardCatalog.getCard("card_cithria")
        val s642 = CombatEngine.drawEnemyCard(
            baseEpicPowerTestState(
                powerIds = listOf("P0642"),
                enemyDrawPool = listOf(enemyTop)
            ).copy(enemyDeckTrapsCount = 40)
        )
        assertTrue(s642.playerHand.any { it.template.id == "card_cithria" && it.isFleeting && it.effectiveCost == 0 })
    }

    @Test
    fun `epic powers group 6 death levelup and passive aura mechanics work`() {
        val handUnit = HandCardInstance(instanceId = "h_u", template = CardCatalog.getCard("card_cithria"), effectiveCost = 2)
        val handEpic = HandCardInstance(instanceId = "h_ep", template = CardCatalog.getCard("card_tiana_crownguard"), effectiveCost = 8)
        val handSpell = HandCardInstance(instanceId = "h_sp", template = CardCatalog.getCard("card_mystic_shot"), effectiveCost = 2)
        val unitInBoard = UnitOnBoard("a_dead", CardCatalog.getCard("card_badgerbear"), true, 3, 4, 4, emptySet(), attachedItems = listOf(CardCatalog.items.first()))

        // Cost auras: P0048 (Spell -2, Unit +1), P0680 (Epic -2), P0222 (First card -2 for 40-card deck)
        val costState = baseEpicPowerTestState(
            powerIds = listOf("P0048", "P0680", "P0222"),
            playerHand = listOf(handUnit, handEpic, handSpell),
            startingDeckSize = 40
        )
        assertEquals(0, costState.effectivePlayCost(handSpell)) // 2 - 2 (P0048) - 2 (P0222) -> 0
        assertEquals(5, costState.effectivePlayCost(handEpic)) // 8 + 1 (P0048) - 2 (P0680) - 2 (P0222) = 5

        // P0378 (give dead ally's attack to hand unit) & P1011 (1 dmg to enemy nexus on ally death)
        val sDeath = CombatEngine.removeUnit(
            baseEpicPowerTestState(
                powerIds = listOf("P0378", "P1011"),
                playerHand = listOf(handUnit),
                playerBoard = listOf(unitInBoard)
            ),
            "a_dead",
            isEnemy = false,
            logs = mutableListOf()
        )
        assertEquals(29, sDeath.enemyNexusHp)
        assertEquals(3, sDeath.playerHand.single().bonusPower)

        // P0671 summons Lux -> Garen -> Galio on champion level up
        val luxUnleveled = UnitOnBoard("u_lux", CardCatalog.getCard("card_lux"), true, 3, 5, 5, emptySet())
        val s671 = CombatEngine.levelUpSpecificChampion(
            baseEpicPowerTestState(powerIds = listOf("P0671"), playerBoard = listOf(luxUnleveled)),
            "u_lux",
            mutableListOf()
        )
        // Lux already on board -> summons Garen
        assertTrue(s671.playerBoard.any { it.template.id == "card_garen" })

        // P0093 keeps temporary buffs permanently (except Barrier)
        val tempAlly = UnitOnBoard(
            instanceId = "u_tmp",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 4,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.QUICK_ATTACK, Keyword.BARRIER)
        )
        val s93Next = CombatEngine.advanceToNextRound(
            baseEpicPowerTestState(powerIds = listOf("P0093"), playerBoard = listOf(tempAlly)).copy(
                roundTempPowerBuffs = mapOf("u_tmp" to 2),
                roundTempHealthBuffs = mapOf("u_tmp" to 2),
                roundTempKeywords = mapOf("u_tmp" to setOf(Keyword.QUICK_ATTACK, Keyword.BARRIER))
            ),
            mutableListOf()
        )
        val kept = s93Next.playerBoard.single()
        assertEquals(4, kept.power)
        assertTrue(kept.keywords.contains(Keyword.QUICK_ATTACK))
        assertFalse(kept.keywords.contains(Keyword.BARRIER))

        // P0549 reduces player nexus damage by 1 (Nexus has Tough)
        val s549 = CombatEngine.dealDamageToPlayerNexus(baseEpicPowerTestState(powerIds = listOf("P0549")), 3, mutableListOf())
        assertEquals(38, s549.playerNexusHp) // took 2 instead of 3

        // P0038 (+3|+3, Overwhelm, Can't Block), P0035 (+2|+2 and 2 dmg at round end), P0914 (+1|+1 per item), P0699 (Daybreak Tough + +0|+1 all)
        val daybreakHand = HandCardInstance(
            instanceId = "h_db",
            template = CardCatalog.getCard("card_cithria").copy(keywords = setOf(Keyword.DAYBREAK)),
            attachedItems = listOf(CardCatalog.items.first(), CardCatalog.items[1]),
            effectiveCost = 1
        )
        val sAura = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0038", "P0035", "P0914", "P0699"),
                playerHand = listOf(daybreakHand),
                playerBoard = listOf(UnitOnBoard("other", CardCatalog.getCard("card_cithria"), true, 2, 2, 2, emptySet()))
            ),
            "h_db"
        )
        val summonedAura = sAura.playerBoard.last()
        assertTrue(summonedAura.keywords.contains(Keyword.OVERWHELM))
        assertTrue(summonedAura.keywords.contains(Keyword.CANT_BLOCK))
        assertTrue(summonedAura.keywords.contains(Keyword.TOUGH))
        assertTrue(summonedAura.power >= 9)
        assertTrue(summonedAura.health >= 10)
        assertTrue(sAura.playerBoard.first { it.instanceId == "other" }.health >= 3)

        // Round end triggers P0035 2 damage to all allies
        val beforeHp = summonedAura.health
        val s35End = CombatEngine.advanceToNextRound(sAura, mutableListOf())
        assertTrue(s35End.playerBoard.last().health < beforeHp)
    }

    @Test
    fun `cross interaction P0385 and P0038 preserves aura stats and keywords after transform`() {
        val cithria = HandCardInstance("h_cit", CardCatalog.getCard("card_cithria"), effectiveCost = 1)
        val played = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0385", "P0038", "P0035", "P0699"),
                playerHand = listOf(cithria)
            ),
            "h_cit"
        )
        assertNotNull(played.pendingTransformChoice)
        val origId = played.pendingTransformChoice!!.targetUnitInstanceId
        val solari = CardCatalog.getCard("card_solari_soldier") // 3|4 Daybreak, Fated
        val transformedState = CombatEngine.transformPlayerUnitToTemplate(
            played,
            origId,
            solari,
            mutableListOf()
        )
        val transformed = transformedState.playerBoard.first { it.instanceId == origId }
        assertEquals(3 + 3 + 2, transformed.power)
        assertEquals(4 + 3 + 2, transformed.health)
        assertEquals(4 + 3 + 2, transformed.maxHealth)
        assertTrue(transformed.keywords.contains(Keyword.OVERWHELM))
        assertTrue(transformed.keywords.contains(Keyword.CANT_BLOCK))
        assertTrue(transformed.keywords.contains(Keyword.TOUGH))

        // Nasus slay aura also re-applies cleanly after transform without double counting
        val nasusTmpl = CardCatalog.getCard("card_nasus")
        val nasusState = CombatEngine.transformPlayerUnitToTemplate(
            transformedState.copy(playerSlainCount = 4),
            origId,
            nasusTmpl,
            mutableListOf()
        )
        val transformedNasus = nasusState.playerBoard.first { it.instanceId == origId }
        assertEquals(nasusTmpl.basePower + 3 + 2 + 4, transformedNasus.power)
        assertEquals(nasusTmpl.baseHealth + 3 + 2 + 4, transformedNasus.health)
    }

    @Test
    fun `cross interaction P0385 and item_shadow_totem transforms original unit not ephemeral copy`() {
        val shadowTotem = CardCatalog.getItem("item_shadow_totem")
        val raiderWithTotem = HandCardInstance(
            instanceId = "h_raider",
            template = CardCatalog.getCard("card_ruthless_raider"),
            attachedItems = listOf(shadowTotem),
            effectiveCost = 2
        )
        val afterPlay = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0385"),
                playerHand = listOf(raiderWithTotem)
            ),
            "h_raider"
        )
        assertEquals(2, afterPlay.playerBoard.size)
        val originalUnit = afterPlay.playerBoard.first()
        val ephemeralCopy = afterPlay.playerBoard.last()
        assertFalse(originalUnit.keywords.contains(Keyword.EPHEMERAL))
        assertTrue(ephemeralCopy.keywords.contains(Keyword.EPHEMERAL))
        assertNotNull(afterPlay.pendingTransformChoice)
        assertEquals(originalUnit.instanceId, afterPlay.pendingTransformChoice!!.targetUnitInstanceId)

        val chosenId = afterPlay.pendingTransformChoice.optionCardIds.first()
        val afterChoice = CombatEngine.resolveFollowerTransformChoice(afterPlay, chosenId)
        assertEquals(chosenId, afterChoice.playerBoard.first { it.instanceId == originalUnit.instanceId }.template.id)
        assertEquals("card_ruthless_raider", afterChoice.playerBoard.first { it.instanceId == ephemeralCopy.instanceId }.template.id)
    }

    @Test
    fun `cross interaction P0047 discards non-fleeting spells across ROUND_END`() {
        val nonFleetingSpell = HandCardInstance(
            instanceId = "sp_reg",
            template = CardCatalog.getCard("card_mystic_shot"),
            effectiveCost = 2,
            isFleeting = false
        )
        val followerInHand = HandCardInstance(
            instanceId = "u_keep",
            template = CardCatalog.getCard("card_cithria"),
            effectiveCost = 1,
            isFleeting = false
        )
        val startState = baseEpicPowerTestState(
            powerIds = listOf("P0047"),
            playerHand = listOf(nonFleetingSpell, followerInHand)
        )
        assertTrue(startState.effectiveIsFleeting(nonFleetingSpell))
        assertFalse(startState.effectiveIsFleeting(followerInHand))

        val nextRound = CombatEngine.advanceToNextRound(startState, mutableListOf())
        assertFalse(nextRound.playerHand.any { it.instanceId == "sp_reg" })
        assertTrue(nextRound.playerHand.any { it.instanceId == "u_keep" })
    }

    @Test
    fun `cross interaction P0752 and P0329 levels up both original and copied champion on first round play`() {
        val luxInHand = HandCardInstance(
            instanceId = "h_lux",
            template = CardCatalog.getCard("card_lux"),
            effectiveCost = 5
        )
        val afterPlay = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0752", "P0329"),
                playerHand = listOf(luxInHand),
                playerMana = 0,
                playerMaxMana = 1
            ),
            "h_lux"
        )
        assertEquals(35, afterPlay.playerNexusHp) // Paid 5 Nexus HP via P0752
        val luxCopies = afterPlay.playerBoard.filter { it.template.id == "card_lux" }
        assertEquals(2, luxCopies.size)
        assertTrue(luxCopies.all { it.isLeveledUp })
    }

    @Test
    fun `cross interaction P0914 stacks on equipment and obeys 3 item cap`() {
        val item0 = CardCatalog.items[0]
        val item1 = CardCatalog.items[1]
        val item2 = CardCatalog.items[2]
        val gearA = HandCardInstance("h_gear_a", CardCatalog.getCard("card_forged_blade"), effectiveCost = 2) // +2|+1
        val gearB = HandCardInstance("h_gear_b", CardCatalog.getCard("card_forged_blade"), effectiveCost = 2) // +2|+1

        // 0 items + 1 equipment with 2x P0914 -> +2|+2 extra from P0914 (1 * 2)
        val unitZero = HandCardInstance("h_u0", CardCatalog.getCard("card_cithria"), effectiveCost = 1)
        var sEq0 = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0914", "P0914"),
                playerHand = listOf(unitZero, gearA, gearB)
            ),
            "h_u0"
        ).copy(priorityHolder = PriorityHolder.PLAYER)
        val u0Id = sEq0.playerBoard.single().instanceId
        sEq0 = CombatEngine.playCardFromHand(sEq0, "h_gear_a", targetUnitId = u0Id).copy(priorityHolder = PriorityHolder.PLAYER)
        val afterGearA = sEq0.playerBoard.single()
        assertEquals(2 + 2 + 2, afterGearA.power) // base 2 + gear 2 + P0914(1*2)
        assertEquals(2 + 1 + 2, afterGearA.health)

        // Replacing gearA with gearB should NOT re-apply P0914
        sEq0 = CombatEngine.playCardFromHand(sEq0, "h_gear_b", targetUnitId = u0Id)
        val afterGearB = sEq0.playerBoard.single()
        assertEquals(2 + 2 + 2, afterGearB.power)
        assertEquals(2 + 1 + 2, afterGearB.health)

        // 3 attached items (already capped at 3) + 1 equipment with 2x P0914 -> 0 extra from P0914
        val unitThree = HandCardInstance(
            "h_u3",
            CardCatalog.getCard("card_cithria"),
            attachedItems = listOf(item0, item1, item2),
            effectiveCost = 1
        )
        var sEq3 = CombatEngine.playCardFromHand(
            baseEpicPowerTestState(
                powerIds = listOf("P0914", "P0914"),
                playerHand = listOf(unitThree, gearA)
            ),
            "h_u3"
        ).copy(priorityHolder = PriorityHolder.PLAYER)
        val u3Before = sEq3.playerBoard.single()
        val u3Id = u3Before.instanceId
        sEq3 = CombatEngine.playCardFromHand(sEq3, "h_gear_a", targetUnitId = u3Id)
        val u3After = sEq3.playerBoard.single()
        assertEquals(u3Before.power + 2, u3After.power)
        assertEquals(u3Before.health + 1, u3After.health)
    }
}
