package com.example.model

/**
 * Complete catalog of 155 Relics imported from relics-en_us.json.
 * Every relic uses its official relicCode (R0000..R0189) as its unique ID.
 */
object RelicCatalog {
    val all: List<RelicTemplate> = listOf(
        RelicTemplate(
            id = "R0153",
            name = "Celestial Staff",
            rarity = Rarity.EPIC,
            description = "Spellshield, Challenger\nEach round, the first time damage is healed from me while you don't have the attack token, Rally.",
            grantedKeywords = setOf(Keyword.SPELLSHIELD, Keyword.CHALLENGER)
        ),
        RelicTemplate(
            id = "R0003",
            name = "Caulfield's Warhammer",
            rarity = Rarity.RARE,
            description = "+2|+1",
            bonusPower = 2,
            bonusHealth = 1
        ),
        RelicTemplate(
            id = "R0175",
            name = "Rhythms of Despair",
            rarity = Rarity.EPIC,
            description = "Power: Allies have \"Nightfall: Grant the weakest enemy 2 Gloom.\""
        ),
        RelicTemplate(
            id = "R0104",
            name = "Swain's Raven Army",
            rarity = Rarity.EPIC,
            description = "Power: Game Start: Drain 5 from the enemy Nexus.\nPlay: If I'm Swain, refill your spell mana."
        ),
        RelicTemplate(
            id = "R0016",
            name = "Ravenous Hydra",
            rarity = Rarity.COMMON,
            description = "When I'm summoned, deal 1 to all enemies."
        ),
        RelicTemplate(
            id = "R0189",
            name = "Overlord's Subject",
            rarity = Rarity.EPIC,
            description = "Play: Summon a Minion.\nMinions have \"Last Breath: Create in hand a Fleeting copy of a spell you played this game\"."
        ),
        RelicTemplate(
            id = "R0130",
            name = "Protectors of Demacia",
            rarity = Rarity.EPIC,
            description = "Power: When you level up an allied champion, summon Lux. If you already have Lux, summon Garen instead. If you have both and not Galio, summon Galio."
        ),
        RelicTemplate(
            id = "R0113",
            name = "Hidden Tome",
            rarity = Rarity.EPIC,
            description = "Power: When you play or level up a champion, refill your spell mana.",
            refillSpellManaOnPlay = true
        ),
        RelicTemplate(
            id = "R0126",
            name = "Great Talons",
            rarity = Rarity.EPIC,
            description = "Power: When you play a 6+ base cost unit, deal 2 to all enemies and the enemy Nexus."
        ),
        RelicTemplate(
            id = "R0014",
            name = "The Gravedigger's Spade",
            rarity = Rarity.RARE,
            description = "Round Start: Draw 1 and give it Fleeting."
        ),
        RelicTemplate(
            id = "R0062",
            name = "Found Fortune",
            rarity = Rarity.EPIC,
            description = "Play: Draw the highest cost card that costs less than my base cost, grant it an Epic item, and reduce its cost to 0."
        ),
        RelicTemplate(
            id = "R0058",
            name = "Chosen by the Stars",
            rarity = Rarity.EPIC,
            description = "Fated, Augment\nEmpowered 10: Overwhelm, Regeneration, and Challenger.",
            grantedKeywords = setOf(Keyword.FATED, Keyword.AUGMENT)
        ),
        RelicTemplate(
            id = "R0082",
            name = "Starforged Gauntlets",
            rarity = Rarity.EPIC,
            description = "Power: Game Start: If I'm in deck or hand and Titanic, +1 Starting Mana. You can find Level 2 Champions when you Invoke or Manifest.",
            startingManaBonus = 1
        ),
        RelicTemplate(
            id = "R0035",
            name = "The Card Master's Gambit",
            rarity = Rarity.RARE,
            description = "+1|+1 and when you win an encounter without taking any Nexus damage, earn 1 Reroll",
            bonusPower = 1,
            bonusHealth = 1
        ),
        RelicTemplate(
            id = "R0101",
            name = "Cosmic Blessing (Consumable)",
            rarity = Rarity.COMMON,
            description = "+1|+1.\nQuest: Win an adventure.\nReward: Gain 5x the normal Champion XP",
            bonusPower = 1,
            bonusHealth = 1
        ),
        RelicTemplate(
            id = "R0102",
            name = "Greater Cosmic Blessing (Consumable)",
            rarity = Rarity.COMMON,
            description = "+1|+1.\nQuest: Win an adventure.\nReward: Gain 10x the normal Champion XP",
            bonusPower = 1,
            bonusHealth = 1
        ),
        RelicTemplate(
            id = "R0080",
            name = "The Beast Within",
            rarity = Rarity.EPIC,
            description = "Power: Allies have Overwhelm, and if they have a subtype, +1|+1.",
            alliesGrantedKeywords = setOf(Keyword.OVERWHELM)
        ),
        RelicTemplate(
            id = "R0183",
            name = "Hecarim's Glaive",
            rarity = Rarity.EPIC,
            description = "Quick Attack\nIf I'm Hecarim, +1 starting mana.\nPower: Game Start: Summon a Shark Chariot.",
            grantedKeyword = Keyword.QUICK_ATTACK
        ),
        RelicTemplate(
            id = "R0017",
            name = "Luden's Tempest",
            rarity = Rarity.RARE,
            description = "All of your spells and skills deal 1 extra damage.",
            spellSkillDamageBonus = 1
        ),
        RelicTemplate(
            id = "R0161",
            name = "Renewing Power",
            rarity = Rarity.EPIC,
            description = "Power: Each round, the first time you play a 6+ cost spell, refill mana equal to its cost."
        ),
        RelicTemplate(
            id = "R0081",
            name = "Transmogulator",
            rarity = Rarity.RARE,
            description = "Support: Transform my supported ally into a random follower that costs 3 more."
        ),
        RelicTemplate(
            id = "R0098",
            name = "Lux's Incandescent Baton",
            rarity = Rarity.EPIC,
            description = "Power: If I'm Lux, +1 starting mana.\nGame Start: Draw a follower that costs 2 or less and grant it Targon's Brace."
        ),
        RelicTemplate(
            id = "R0010",
            name = "Heart of Gold",
            rarity = Rarity.RARE,
            description = "You earn 50% additional gold during adventures.",
            goldMultiplierBonus = 0.5
        ),
        RelicTemplate(
            id = "R0084",
            name = "Cosmic Pearl (Consumable)",
            rarity = Rarity.COMMON,
            description = "+1|+1.\nQuest: Win an adventure.\nReward: Gain 5x the normal Champion XP",
            bonusPower = 1,
            bonusHealth = 1
        ),
        RelicTemplate(
            id = "R0144",
            name = "Emotional Support Bear",
            rarity = Rarity.EPIC,
            description = "Power: Game Start: Shuffle 3 Tibbers into your deck.\nYour Tibbers cost 2 less and have +5|+5."
        ),
        RelicTemplate(
            id = "R0090",
            name = "Wicked Harvest",
            rarity = Rarity.RARE,
            description = "Play: Deal 3 to all other units."
        ),
        RelicTemplate(
            id = "R0132",
            name = "Scoundrel Hunter",
            rarity = Rarity.EPIC,
            description = "Challenger\nPower: When you play a champion, summon a Warlord's Palace or advance it 2 rounds.",
            grantedKeyword = Keyword.CHALLENGER
        ),
        RelicTemplate(
            id = "R0021",
            name = "Dreadway Chase Gun",
            rarity = Rarity.RARE,
            description = "When I'm summoned, create 2 Warning Shots in hand."
        ),
        RelicTemplate(
            id = "R0184",
            name = "Demacia's Will",
            rarity = Rarity.EPIC,
            description = "SpellShield, Sharpsight\nIf I'm Garen, my non-combat strikes deal excess damage to the enemy Nexus.",
            grantedKeywords = setOf(Keyword.SPELLSHIELD, Keyword.SHARPSIGHT)
        ),
        RelicTemplate(
            id = "R0033",
            name = "Turret Plating",
            rarity = Rarity.RARE,
            description = "Your Nexus has Tough.",
            nexusTough = true
        ),
        RelicTemplate(
            id = "R0143",
            name = "Frost Breath",
            rarity = Rarity.EPIC,
            description = "Tough\nPlay: Frostbite the strongest enemy. If I'm Anivia, Frostbite ALL enemies instead.",
            grantedKeyword = Keyword.TOUGH
        ),
        RelicTemplate(
            id = "R0002",
            name = "The Troll King's Crusher",
            rarity = Rarity.COMMON,
            description = "Overwhelm",
            grantedKeyword = Keyword.OVERWHELM
        ),
        RelicTemplate(
            id = "R0114",
            name = "Black Shield",
            rarity = Rarity.EPIC,
            description = "Power: When you summon an ally, grant it SpellShield."
        ),
        RelicTemplate(
            id = "R0180",
            name = "Space Puppy!!!",
            rarity = Rarity.EPIC,
            description = "SpellShield\nPlay: Invoke a Celestial card that costs 7 or more. You can find Aurelion Sol in this Invoke.",
            grantedKeyword = Keyword.SPELLSHIELD
        ),
        RelicTemplate(
            id = "R0049",
            name = "Voidborne Carapace",
            rarity = Rarity.RARE,
            description = "Evolve\nWhen ANY unit dies, grant me its keywords.",
            grantedKeyword = Keyword.EVOLVE
        ),
        RelicTemplate(
            id = "R0024",
            name = "Guinsoo's Rageblade",
            rarity = Rarity.COMMON,
            description = "Attack: Grant me +1|+1.",
            attackGainPlusOnePlusOne = true
        ),
        RelicTemplate(
            id = "R0142",
            name = "Spirit Glove",
            rarity = Rarity.EPIC,
            description = "Overwhelm\nGame Start: If I'm a Spirit Blossom unit, set my cost to 10.",
            grantedKeyword = Keyword.OVERWHELM
        ),
        RelicTemplate(
            id = "R0164",
            name = "Ranger's Command",
            rarity = Rarity.EPIC,
            description = "+1|+0 and Quick Attack.\nPower: Round Start: If you have the attack token, grant a random ally Scout.",
            bonusPower = 1,
            grantedKeyword = Keyword.QUICK_ATTACK
        ),
        RelicTemplate(
            id = "R0079",
            name = "Spirit of the Buhru",
            rarity = Rarity.EPIC,
            description = "Power: Allies that cost 1 or less have Overwhelm, Quick Attack, and Fury."
        ),
        RelicTemplate(
            id = "R0162",
            name = "Makeshift Mayhem",
            rarity = Rarity.RARE,
            description = "Play: Create 3 random cards in hand. They cost 1 and are Fleeting."
        ),
        RelicTemplate(
            id = "R0000",
            name = "The Grand Duelist's Blade",
            rarity = Rarity.COMMON,
            description = "Challenger",
            grantedKeyword = Keyword.CHALLENGER
        ),
        RelicTemplate(
            id = "R0004",
            name = "Jaurim's Fist",
            rarity = Rarity.RARE,
            description = "+1|+2",
            bonusPower = 1,
            bonusHealth = 2
        ),
        RelicTemplate(
            id = "R0099",
            name = "Icon of Valhir",
            rarity = Rarity.EPIC,
            description = "Power: Your elemental spells and Skills deal 2 extra damage.",
            elementalSpellSkillDamageBonus = 2
        ),
        RelicTemplate(
            id = "R0178",
            name = "The Equalizer",
            rarity = Rarity.RARE,
            description = "Play: Shrink the strongest enemy to my stats."
        ),
        RelicTemplate(
            id = "R0128",
            name = "Treasures of the Deep",
            rarity = Rarity.EPIC,
            description = "Deep and I am a Sea Monster.\nWhen I'm summoned, if you aren't Deep, Toss equal to my cost.",
            grantedKeyword = Keyword.DEEP
        ),
        RelicTemplate(
            id = "R0026",
            name = "Luminous Orb",
            rarity = Rarity.RARE,
            description = "+0|+3 and Sharpsight.",
            bonusHealth = 3,
            grantedKeyword = Keyword.SHARPSIGHT
        ),
        RelicTemplate(
            id = "R0148",
            name = "Azir's Staff of Office",
            rarity = Rarity.EPIC,
            description = "+1|+1\nIf I'm an Ascended, I cost 1 less.\nPlay: Advance your Buried Sun Disc 3 rounds.",
            bonusPower = 1,
            bonusHealth = 1
        ),
        RelicTemplate(
            id = "R0011",
            name = "The Deceiver's Crest",
            rarity = Rarity.RARE,
            description = "When I level up, create a copy of my champion spell in hand. It costs 0 this round."
        ),
        RelicTemplate(
            id = "R0092",
            name = "Spectral Scissors",
            rarity = Rarity.EPIC,
            description = "+2|+0\nSupport: If my supported ally is a follower, grant it Ephemeral and summon an exact copy of it attacking.",
            bonusPower = 2,
            grantedKeyword = Keyword.SUPPORT
        ),
        RelicTemplate(
            id = "R0123",
            name = "Searing Brilliance",
            rarity = Rarity.EPIC,
            description = "Power: Game Start: Grant a random spell from your deck an item with \"-1 cost and draw 1\". Create 4 copies of it in your deck."
        ),
        RelicTemplate(
            id = "R0120",
            name = "Twin Drakehounds",
            rarity = Rarity.EPIC,
            description = "Overwhelm\nAttack: Deal 2 to the enemy Nexus. If I've already attacked this round, deal 2 to all enemies as well.",
            grantedKeyword = Keyword.OVERWHELM
        ),
        RelicTemplate(
            id = "R0022",
            name = "The Starchild's Staff",
            rarity = Rarity.COMMON,
            description = "Start adventures with +5 Regen",
            adventureRegenBonus = 5
        ),
        RelicTemplate(
            id = "R0045",
            name = "Corrupted Star Fragment",
            rarity = Rarity.RARE,
            description = "Support: Kill my supported ally and grant me its keywords and stats.",
            grantedKeyword = Keyword.SUPPORT
        ),
        RelicTemplate(
            id = "R0038",
            name = "The Curator's Gatebreaker",
            rarity = Rarity.RARE,
            description = "-1|-0\nPlay: I strike the enemy Nexus.",
            bonusPower = -1
        ),
        RelicTemplate(
            id = "R0131",
            name = "Warlord's Helmet",
            rarity = Rarity.EPIC,
            description = "+10 Nexus Health.\nPower: If I'm Tryndamere, when you grant stats to a unit in your deck, grant twice as much.",
            adventureMaxHpBonus = 10
        ),
        RelicTemplate(
            id = "R0141",
            name = "The Taker's Bow",
            rarity = Rarity.EPIC,
            description = "Power: Attacking Ephemeral allies can't take damage or die."
        ),
        RelicTemplate(
            id = "R0050",
            name = "Galeforce",
            rarity = Rarity.RARE,
            description = "Scout\nRound End: Shuffle me into your deck.",
            grantedKeyword = Keyword.SCOUT
        ),
        RelicTemplate(
            id = "R0111",
            name = "Big Guns",
            rarity = Rarity.EPIC,
            description = "Power: Your spells and skills deal 1 extra damage.",
            spellSkillDamageBonus = 1
        ),
        RelicTemplate(
            id = "R0100",
            name = "Shock & Awe",
            rarity = Rarity.EPIC,
            description = "Power: When an ally that costs 1 or less attacks, deal 1 to the enemy Nexus."
        ),
        RelicTemplate(
            id = "R0005",
            name = "Stormrazor",
            rarity = Rarity.COMMON,
            description = "Quick Attack",
            grantedKeyword = Keyword.QUICK_ATTACK
        ),
        RelicTemplate(
            id = "R0127",
            name = "Scales of Judgment",
            rarity = Rarity.EPIC,
            description = "Power: When you play a champion, it strikes the weakest enemy. If it's Nasus, it also strikes the strongest enemy.",
            strikeWeakestOnSummon = true
        ),
        RelicTemplate(
            id = "R0107",
            name = "Hextech Rifle",
            rarity = Rarity.EPIC,
            description = "Power: Game Start: Plant 6 Flashbomb Traps in the top 8 cards of the enemy deck. When a trap misses, replant it in the top 8 cards of the enemy deck."
        ),
        RelicTemplate(
            id = "R0043",
            name = "Star Gem",
            rarity = Rarity.RARE,
            description = "Allied champions have +1|+1 and cost 2 less."
        ),
        RelicTemplate(
            id = "R0052",
            name = "Guardian's Orb",
            rarity = Rarity.RARE,
            description = "When I'm summoned, grant Epic items to 3 random cards in your deck."
        ),
        RelicTemplate(
            id = "R0051",
            name = "Golden Spatula",
            rarity = Rarity.RARE,
            description = "When I'm summoned, grant me +1|+1 and a random keyword for every 30 cards that started in your deck."
        ),
        RelicTemplate(
            id = "R0027",
            name = "The Bounty Hunter's Renown",
            rarity = Rarity.RARE,
            description = "I have +1|+1 for every 200 gold you have."
        ),
        RelicTemplate(
            id = "R0160",
            name = "Whirling Blade",
            rarity = Rarity.EPIC,
            description = "When I'm summoned or Attack: Deal 1 to all enemies and the enemy Nexus."
        ),
        RelicTemplate(
            id = "R0075",
            name = "Condenser",
            rarity = Rarity.RARE,
            description = "Support: Create a 1 cost 1|1 copy of my supported ally in hand.",
            grantedKeyword = Keyword.SUPPORT
        ),
        RelicTemplate(
            id = "R0013",
            name = "Archangel's Staff",
            rarity = Rarity.RARE,
            description = "Round Start: Refill your spell mana.",
            refillSpellManaRoundStart = true
        ),
        RelicTemplate(
            id = "R0065",
            name = "Packed Powder",
            rarity = Rarity.EPIC,
            description = "Plunder: I cost 2 less.\nI have +1|+1 for each different round you've damaged the enemy Nexus."
        ),
        RelicTemplate(
            id = "R0181",
            name = "Harsh Lesson",
            rarity = Rarity.EPIC,
            description = "Regeneration\nWhen I'm blocked, your strongest Tentacle strikes my blocker.\nPlay: Spawn 2.",
            grantedKeyword = Keyword.REGENERATION
        ),
        RelicTemplate(
            id = "R0028",
            name = "The Chameleon's Necklace",
            rarity = Rarity.COMMON,
            description = "Game Start: Create 2 copies of me in your deck."
        ),
        RelicTemplate(
            id = "R0136",
            name = "Mischiefmaker's Flute",
            rarity = Rarity.EPIC,
            description = "Spellshield\nWhen I deal damage, plant that many Poison Puffcaps in the enemy deck.",
            grantedKeyword = Keyword.SPELLSHIELD
        ),
        RelicTemplate(
            id = "R0040",
            name = "Stalker's Blade",
            rarity = Rarity.RARE,
            description = "When I'm summoned, I strike the weakest enemy.",
            strikeWeakestOnSummon = true
        ),
        RelicTemplate(
            id = "R0044",
            name = "Guardian's Trinket",
            rarity = Rarity.RARE,
            description = "Adventure Start: Add 2 copies of a random Champion to your deck. Attach a random Epic item to it."
        ),
        RelicTemplate(
            id = "R0140",
            name = "Attendant Spirits",
            rarity = Rarity.EPIC,
            description = "Power: Ephemeral allies have Overwhelm.\nPlay: If I'm a Spirit Blossom unit, Summon 2 Fox-Fires."
        ),
        RelicTemplate(
            id = "R0012",
            name = "The Scourge's Stash",
            rarity = Rarity.RARE,
            description = "Plunder: I cost 2 less."
        ),
        RelicTemplate(
            id = "R0097",
            name = "Fear-Cleaving Axe",
            rarity = Rarity.EPIC,
            description = "Spirit\nAttack: Boost all units.",
            grantedKeyword = Keyword.SPIRIT,
            spiritStacks = 1
        ),
        RelicTemplate(
            id = "R0029",
            name = "The Collector",
            rarity = Rarity.RARE,
            description = "+1|+1 and when I kill an enemy, you earn 50 gold. (Max 2 kills per encounter)",
            bonusPower = 1,
            bonusHealth = 1
        ),
        RelicTemplate(
            id = "R0091",
            name = "Strength of Stone",
            rarity = Rarity.EPIC,
            description = "+0|+2\nPower: When you summon an ally, grant it +0|+1 and Formidable",
            bonusHealth = 2
        ),
        RelicTemplate(
            id = "R0103",
            name = "Yasuo's Windblade",
            rarity = Rarity.EPIC,
            description = "Power: If I'm Yasuo, +1 starting mana. When you Recall an enemy, increase its cost by 2."
        ),
        RelicTemplate(
            id = "R0054",
            name = "Wriggle's Lantern",
            rarity = Rarity.RARE,
            description = "Round End: Grant me Impact twice if I'm in hand (max Impact 10)."
        ),
        RelicTemplate(
            id = "R0187",
            name = "Resonant Shard",
            rarity = Rarity.EPIC,
            description = "+0|+2 and SpellShield\nWhen you destroy an allied landmark or mana gem, deal 1 to a random enemy 3 times.",
            bonusHealth = 2,
            grantedKeyword = Keyword.SPELLSHIELD
        ),
        RelicTemplate(
            id = "R0053",
            name = "Succubus's Brand",
            rarity = Rarity.RARE,
            description = "+1|+1 and when I kill a unit, summon a random husk.",
            bonusPower = 1,
            bonusHealth = 1
        ),
        RelicTemplate(
            id = "R0018",
            name = "Everfrost",
            rarity = Rarity.COMMON,
            description = "When I'm summoned, Stun the strongest enemy."
        ),
        RelicTemplate(
            id = "R0109",
            name = "Frozen Tomb",
            rarity = Rarity.EPIC,
            description = "Game Start: If I'm in deck or hand, summon a Stormcarved Spire with Countdown 4: Summon me."
        ),
        RelicTemplate(
            id = "R0151",
            name = "Royal Nibbles",
            rarity = Rarity.EPIC,
            description = "+1|+1, Regeneration\nI cost 1 less for each Poro ally in play.",
            bonusPower = 1,
            bonusHealth = 1,
            grantedKeyword = Keyword.REGENERATION
        ),
        RelicTemplate(
            id = "R0030",
            name = "Armordillo Shell",
            rarity = Rarity.COMMON,
            description = "+0|+1 and Tough",
            bonusHealth = 1,
            grantedKeyword = Keyword.TOUGH
        ),
        RelicTemplate(
            id = "R0154",
            name = "Yeti Spotting Guide",
            rarity = Rarity.EPIC,
            description = "Play: Shuffle 3 Snowballs into your deck.\nAttack: Grant +1|+1 to me and to all Snowballs in your deck."
        ),
        RelicTemplate(
            id = "R0169",
            name = "Blood Brand",
            rarity = Rarity.RARE,
            description = "When you play another ally, deal 1 to it and grant me +1|+1."
        ),
        RelicTemplate(
            id = "R0112",
            name = "Living Weapon",
            rarity = Rarity.EPIC,
            description = "Play, Attack, and Round Start: Forge me."
        ),
        RelicTemplate(
            id = "R0061",
            name = "Echoing Spirit",
            rarity = Rarity.EPIC,
            description = "Game Start: Create 7 copies of me in deck. Champion Spells cost 1 less."
        ),
        RelicTemplate(
            id = "R0083",
            name = "Greater Cosmic Pearl (Consumable)",
            rarity = Rarity.COMMON,
            description = "+1|+1.\nQuest: Win an adventure.\nReward: Gain 10x the normal Champion XP",
            bonusPower = 1,
            bonusHealth = 1
        ),
        RelicTemplate(
            id = "R0147",
            name = "The Darkin Blade",
            rarity = Rarity.EPIC,
            description = "+0|+3\nPower: When you play or transform a Darkin, it strikes the weakest enemy.",
            bonusHealth = 3
        ),
        RelicTemplate(
            id = "R0006",
            name = "Warmog's Armor",
            rarity = Rarity.COMMON,
            description = "Regeneration",
            grantedKeyword = Keyword.REGENERATION
        ),
        RelicTemplate(
            id = "R0015",
            name = "Lost Chapter",
            rarity = Rarity.COMMON,
            description = "Play: Refill your spell mana.",
            refillSpellManaOnPlay = true
        ),
        RelicTemplate(
            id = "R0039",
            name = "True Ice Flail",
            rarity = Rarity.COMMON,
            description = "When I'm summoned, give enemies Vulnerable this round."
        ),
        RelicTemplate(
            id = "R0166",
            name = "Price of Retribution",
            rarity = Rarity.EPIC,
            description = "+2|+0 and Deathless.\nPower: When an ally dies, deal 1 to the enemy Nexus.",
            bonusPower = 2,
            grantedKeyword = Keyword.DEATHLESS
        ),
        RelicTemplate(
            id = "R0019",
            name = "The Berserker's Buckle",
            rarity = Rarity.RARE,
            description = "When I survive damage, grant me +2|+2."
        ),
        RelicTemplate(
            id = "R0036",
            name = "The Loose Cannon's Payload",
            rarity = Rarity.RARE,
            description = "When I'm summoned, discard your hand then create that many Pow-Pows in hand."
        ),
        RelicTemplate(
            id = "R0149",
            name = "Draconic Runes",
            rarity = Rarity.EPIC,
            description = "If I'm a Dragon, I have +4|+0 and Overwhelm. Otherwise, I have +2|+0 and Fury.",
            bonusPower = 2,
            grantedKeyword = Keyword.FURY
        ),
        RelicTemplate(
            id = "R0086",
            name = "Spellweaver's Symphony",
            rarity = Rarity.EPIC,
            description = "Power: When you gain the attack token, create a random spell that costs 2 from your regions in hand. It costs 0 and is Fleeting."
        ),
        RelicTemplate(
            id = "R0138",
            name = "Siphoned Sorrow",
            rarity = Rarity.EPIC,
            description = "When I'm summoned or Round Start: I steal up to 3 Health from a random enemy unit. It can't drop below 1 health."
        ),
        RelicTemplate(
            id = "R0137",
            name = "Whisper",
            rarity = Rarity.EPIC,
            description = "Power: Your Skills deal 1 extra damage per Stunned enemy."
        ),
        RelicTemplate(
            id = "R0135",
            name = "Champion's Purse",
            rarity = Rarity.EPIC,
            description = "+2|+0 and Overwhelm.\nWhen I kill an enemy in combat, create 2 Coins in hand.",
            bonusPower = 2,
            grantedKeyword = Keyword.OVERWHELM
        ),
        RelicTemplate(
            id = "R0139",
            name = "Soul Lantern",
            rarity = Rarity.EPIC,
            description = "Tough, SpellShield\nAttack: If I'm Thresh, summon an attacking Ephemeral copy of a champion in your deck with less Power.",
            grantedKeywords = setOf(Keyword.TOUGH, Keyword.SPELLSHIELD)
        ),
        RelicTemplate(
            id = "R0020",
            name = "The Grand General's Counterplan",
            rarity = Rarity.RARE,
            description = "Round Start: Create a Fleeting copy of me in hand.",
            createChampionSpellRoundStart = true
        ),
        RelicTemplate(
            id = "R0059",
            name = "Death's Foil",
            rarity = Rarity.EPIC,
            description = "+1|+0\nWhile I'm attacking, I can't take damage or die.",
            bonusPower = 1
        ),
        RelicTemplate(
            id = "R0094",
            name = "Norra's Portal Accelerator",
            rarity = Rarity.EPIC,
            description = "Power: Game Start: If I'm Norra, +1 starting mana.\nWhen you summon a created ally, grant it Impact."
        ),
        RelicTemplate(
            id = "R0146",
            name = "The Hand of Noxus",
            rarity = Rarity.EPIC,
            description = "+2|+0 and Challenger.\nPlay: Play Apprehend on the strongest enemy.",
            bonusPower = 2,
            grantedKeyword = Keyword.CHALLENGER
        ),
        RelicTemplate(
            id = "R0069",
            name = "Spirit Forge (Consumable)",
            rarity = Rarity.RARE,
            description = "Quest: Win a 3+ Star Adventure\nReward: Upgrade a Rare Relic Slot on this champion to Epic!"
        ),
        RelicTemplate(
            id = "R0106",
            name = "Deadly Harpoon",
            rarity = Rarity.EPIC,
            description = "+2|+0 and Challenger.\nPower: Game Start: Shuffle two Deaths From Below into your deck and grant Deaths From Below a random epic item.",
            bonusPower = 2,
            grantedKeyword = Keyword.CHALLENGER
        ),
        RelicTemplate(
            id = "R0001",
            name = "Crownguard Inheritance",
            rarity = Rarity.RARE,
            description = "When I level up, Rally.",
            levelUpRally = true
        ),
        RelicTemplate(
            id = "R0118",
            name = "Defense Spending",
            rarity = Rarity.EPIC,
            description = "Power: Your Nexus has Tough.\nI am Tech.",
            nexusTough = true
        ),
        RelicTemplate(
            id = "R0008",
            name = "Z-Drive Prototype",
            rarity = Rarity.COMMON,
            description = "Start adventures with +2 Rerolls",
            adventureStartingRerollsBonus = 2
        ),
        RelicTemplate(
            id = "R0150",
            name = "Queen's Embrace",
            rarity = Rarity.EPIC,
            description = "Spirit, Impact, Evolve.\nPower: Fearsome allies can only be blocked by units with 5+ Power.",
            grantedKeywords = setOf(Keyword.SPIRIT, Keyword.IMPACT, Keyword.EVOLVE),
            impactStacks = 1,
            spiritStacks = 1
        ),
        RelicTemplate(
            id = "R0034",
            name = "Riptide Battery",
            rarity = Rarity.RARE,
            description = "Plunder: Play Cannon Barrage a number of times equal to my cost."
        ),
        RelicTemplate(
            id = "R0037",
            name = "Hymn of Valor",
            rarity = Rarity.RARE,
            description = "When I'm summoned, create a Redoubled Valor in hand. It costs 3."
        ),
        RelicTemplate(
            id = "R0155",
            name = "Innervating Locket",
            rarity = Rarity.EPIC,
            description = "Power: When you play a Stance Swap, heal your Nexus 2 and refill 1 spell mana."
        ),
        RelicTemplate(
            id = "R0116",
            name = "Stacked Deck",
            rarity = Rarity.EPIC,
            description = "+200 Starting Gold.\nPower: Allies have +1|+1 for every 12 cards in your deck past 18 at game start.",
            adventureStartingGoldBonus = 200
        ),
        RelicTemplate(
            id = "R0048",
            name = "Chemtech Duplicator",
            rarity = Rarity.RARE,
            description = "When you play a spell, if you have 6+ mana gems, copy it with the same targets."
        ),
        RelicTemplate(
            id = "R0117",
            name = "Siren's Call",
            rarity = Rarity.EPIC,
            description = "Power: Game Start: Create an Evelynn in hand. When you summon a Husk, it loses Immobile."
        ),
        RelicTemplate(
            id = "R0046",
            name = "Arcane Comet",
            rarity = Rarity.RARE,
            description = "Round Start: Create a Fleeting Falling Comet in hand."
        ),
        RelicTemplate(
            id = "R0085",
            name = "Secret Technique",
            rarity = Rarity.EPIC,
            description = "Power: When an ally gains stats from a spell, it gains twice that many instead."
        ),
        RelicTemplate(
            id = "R0119",
            name = "Essence Theft",
            rarity = Rarity.EPIC,
            description = "When you Recall another unit, grant me +1|+0.\nPower: Game Start: Create a Homecoming in hand and grant it Double Time Watch."
        ),
        RelicTemplate(
            id = "R0041",
            name = "Greenglade Shadeleaf",
            rarity = Rarity.RARE,
            description = "Support: Grant my supported ally Elusive.",
            grantedKeyword = Keyword.SUPPORT
        ),
        RelicTemplate(
            id = "R0025",
            name = "Troll King's Crown",
            rarity = Rarity.RARE,
            description = "Allies have Overwhelm.",
            alliesGrantedKeywords = setOf(Keyword.OVERWHELM)
        ),
        RelicTemplate(
            id = "R0047",
            name = "Quest for Wisdom",
            rarity = Rarity.COMMON,
            description = "+1|+1.\nQuest: Win an adventure of 1-Star difficulty or lower.\nReward: Gain 10x the normal Champion XP",
            bonusPower = 1,
            bonusHealth = 1
        ),
        RelicTemplate(
            id = "R0089",
            name = "Full Build",
            rarity = Rarity.EPIC,
            description = "Challenger, Tough, and Overwhelm",
            grantedKeywords = setOf(Keyword.CHALLENGER, Keyword.TOUGH, Keyword.OVERWHELM)
        ),
        RelicTemplate(
            id = "R0177",
            name = "Fate's Hand",
            rarity = Rarity.RARE,
            description = "Game Start: Add 10 random cards to your deck. Grant each a Common item, a Rare item, and an Epic item."
        ),
        RelicTemplate(
            id = "R0133",
            name = "Shield of Daybreak",
            rarity = Rarity.EPIC,
            description = "Power: Allies with Daybreak have Tough. When an ally activates Daybreak, grant allies +0|+1."
        ),
        RelicTemplate(
            id = "R0170",
            name = "Pack-A-Punch",
            rarity = Rarity.RARE,
            description = "Allies have Impact.",
            alliesGrantedKeywords = setOf(Keyword.IMPACT),
            alliesImpactStacks = 1
        ),
        RelicTemplate(
            id = "R0125",
            name = "Perfect Hex Core",
            rarity = Rarity.EPIC,
            description = "Power: Round Start: Create a Hex Core Upgrade in hand. Your Hex Core Upgrades have Stopwatch and can upgrade any ally."
        ),
        RelicTemplate(
            id = "R0159",
            name = "Sentinel's Plate",
            rarity = Rarity.EPIC,
            description = "Barrier\nWhen I survive damage, grant me +3|+3 and a random keyword.",
            grantedKeyword = Keyword.BARRIER
        ),
        RelicTemplate(
            id = "R0064",
            name = "Oath of the Guardians",
            rarity = Rarity.EPIC,
            description = "Play: Shuffle 5 level 2 champions into your deck and double their stats, then draw one of them."
        ),
        RelicTemplate(
            id = "R0066",
            name = "Portal Pals",
            rarity = Rarity.EPIC,
            description = "When I'm summoned, create 2 random 7+ cost units from your regions and reduce their cost by my base cost."
        ),
        RelicTemplate(
            id = "R0009",
            name = "Banshee's Veil",
            rarity = Rarity.COMMON,
            description = "SpellShield",
            grantedKeyword = Keyword.SPELLSHIELD
        ),
        RelicTemplate(
            id = "R0182",
            name = "Gale Gauntlet",
            rarity = Rarity.EPIC,
            description = "+1|+1 and Attune.\nPower: Game Start: If I'm Janna, create a Divine Grace in your hand.",
            bonusPower = 1,
            bonusHealth = 1,
            grantedKeyword = Keyword.ATTUNE
        ),
        RelicTemplate(
            id = "R0185",
            name = "Mark of the Kindred",
            rarity = Rarity.EPIC,
            description = "Deathless\nWhen I'm summoned, mark the weakest enemy and shrink its health to 1.",
            grantedKeyword = Keyword.DEATHLESS
        ),
        RelicTemplate(
            id = "R0096",
            name = "Dreams Of Yordles",
            rarity = Rarity.COMMON,
            description = "Spirit, Impact",
            grantedKeywords = setOf(Keyword.SPIRIT, Keyword.IMPACT),
            impactStacks = 1,
            spiritStacks = 1
        ),
        RelicTemplate(
            id = "R0124",
            name = "Starfire Spellblade",
            rarity = Rarity.EPIC,
            description = "Power: If I'm Kayle, +1 Starting Mana.\nI have +1|+0 for each encounter you've won this adventure."
        ),
        RelicTemplate(
            id = "R0121",
            name = "Harmless Scarecrow",
            rarity = Rarity.EPIC,
            description = "Power: When you play a card that costs 3+ mana, Terrify 1. While the enemy deck has 15 or fewer cards, allies have +2|+2 and Overwhelm."
        ),
        RelicTemplate(
            id = "R0122",
            name = "Cruel Experiments",
            rarity = Rarity.EPIC,
            description = "Power: When you play an ally with 2+ Health, deal 1 to it and grant it Overwhelm and Impact."
        ),
        RelicTemplate(
            id = "R0108",
            name = "Cease and Desist",
            rarity = Rarity.EPIC,
            description = "3 Impact\nPlay: If I have 10+ Power, give me Barrier and start a free attack Challenging the strongest enemy.",
            grantedKeyword = Keyword.IMPACT,
            impactStacks = 3
        ),
        RelicTemplate(
            id = "R0186",
            name = "Kinkou Tradition",
            rarity = Rarity.EPIC,
            description = "Elusive\nWhen an ally gets Barrier, grant them a random keyword and refill 1 mana.",
            grantedKeyword = Keyword.ELUSIVE
        ),
        RelicTemplate(
            id = "R0105",
            name = "Jayce's Hextech Battery",
            rarity = Rarity.EPIC,
            description = "+1|+2.\nWhen I level up, refill 3 mana. If I'm Jayce, refill 6 mana instead.",
            bonusPower = 1,
            bonusHealth = 2
        ),
        RelicTemplate(
            id = "R0145",
            name = "Timehonored Blade",
            rarity = Rarity.EPIC,
            description = "If I'm a Spirit Blossom unit, I cost 1 less for each different round you targeted an ally."
        ),
        RelicTemplate(
            id = "R0023",
            name = "Laurent Bladerack",
            rarity = Rarity.RARE,
            description = "Allies have Challenger.",
            alliesGrantedKeywords = setOf(Keyword.CHALLENGER)
        ),
        RelicTemplate(
            id = "R0031",
            name = "Tempest Blade",
            rarity = Rarity.RARE,
            description = "When I level up, Stun all enemies."
        ),
        RelicTemplate(
            id = "R0095",
            name = "Utmost Despair",
            rarity = Rarity.RARE,
            description = "Play: Grant enemies Gloom."
        ),
        RelicTemplate(
            id = "R0060",
            name = "Disciple of Shadows",
            rarity = Rarity.EPIC,
            description = "I cost 1 less for each ally in play.\nPlay: Deal 3 to all other allies."
        ),
        RelicTemplate(
            id = "R0134",
            name = "Deck of Destiny",
            rarity = Rarity.EPIC,
            description = "+1|+1\nPower: Game Start: Create a 1 cost Wild Card in your deck for every 7 cards in your deck.",
            bonusPower = 1,
            bonusHealth = 1
        ),
        RelicTemplate(
            id = "R0007",
            name = "Guardian Angel",
            rarity = Rarity.RARE,
            description = "+0|+2 and Deathless.\nStart adventures with +2 Revives.",
            bonusHealth = 2,
            grantedKeyword = Keyword.DEATHLESS,
            adventureStartingRevivesBonus = 2
        ),
        RelicTemplate(
            id = "R0115",
            name = "Loaded Dice",
            rarity = Rarity.EPIC,
            description = "+3 Rerolls\n+10% to find Epic powers and items.",
            adventureStartingRerollsBonus = 3
        ),
        RelicTemplate(
            id = "R0032",
            name = "Soul Spear",
            rarity = Rarity.COMMON,
            description = "+1|+0 and Fearsome",
            bonusPower = 1,
            grantedKeyword = Keyword.FEARSOME
        )
    )
}
