package com.example.model

import android.util.Log

object CardCatalog {

    private const val TAG = "CardCatalog"

    internal var missingIdObserver: ((kind: String, id: String) -> Unit)? = null

    private fun logMissingId(kind: String, id: String) {
        missingIdObserver?.invoke(kind, id)
        val msg = "Invalid or missing $kind ID requested: '$id'"
        try {
            Log.e(TAG, msg)
        } catch (_: RuntimeException) {
            System.err.println("[$TAG] $msg")
        }
    }

    val items: List<ItemTemplate> = listOf(
        ItemTemplate(
            id = "item_studded_leather",
            name = "Giáp Da Nạm Đinh",
            rarity = Rarity.COMMON,
            category = ItemCategory.STAT_KEYWORD,
            description = "+1|+1",
            bonusPower = 1,
            bonusHealth = 1
        ),
        ItemTemplate(
            id = "item_phage",
            name = "Búa Gỗ",
            rarity = Rarity.RARE,
            category = ItemCategory.STAT_KEYWORD,
            description = "+2|+2",
            bonusPower = 2,
            bonusHealth = 2
        ),
        ItemTemplate(
            id = "item_pickaxe",
            name = "Cuốc Chim",
            rarity = Rarity.COMMON,
            category = ItemCategory.STAT_KEYWORD,
            description = "+2|+0",
            bonusPower = 2
        ),
        ItemTemplate(
            id = "item_giants_belt",
            name = "Đai Khổng Lồ",
            rarity = Rarity.COMMON,
            category = ItemCategory.STAT_KEYWORD,
            description = "+0|+2",
            bonusHealth = 2
        ),
        ItemTemplate(
            id = "item_quickstrike",
            name = "Kiếm Xung Kích",
            rarity = Rarity.RARE,
            category = ItemCategory.STAT_KEYWORD,
            description = "Tấn Công Nhanh",
            grantedKeyword = Keyword.QUICK_ATTACK
        ),
        ItemTemplate(
            id = "item_double_strike",
            name = "Cổ Ngữ Liên Kích",
            rarity = Rarity.EPIC,
            category = ItemCategory.STAT_KEYWORD,
            description = "Tấn Công Kép",
            grantedKeyword = Keyword.DOUBLE_ATTACK
        ),
        ItemTemplate(
            id = "item_colossus",
            name = "Búa Khổng Lồ",
            rarity = Rarity.EPIC,
            category = ItemCategory.STAT_KEYWORD,
            description = "+3|+3 và Áp Đảo",
            bonusPower = 3,
            bonusHealth = 3,
            grantedKeyword = Keyword.OVERWHELM
        ),
        ItemTemplate(
            id = "item_elusive_cloak",
            name = "Áo Choàng Bóng Tối",
            rarity = Rarity.RARE,
            category = ItemCategory.STAT_KEYWORD,
            description = "Huyền Ảo",
            grantedKeyword = Keyword.ELUSIVE
        ),
        ItemTemplate(
            id = "item_lifesteal_orb",
            name = "Quyền Trượng Hút Máu",
            rarity = Rarity.EPIC,
            category = ItemCategory.STAT_KEYWORD,
            description = "Hút Máu",
            grantedKeyword = Keyword.LIFESTEAL
        ),
        ItemTemplate(
            id = "item_deathless_aegis",
            name = "Khiên Bất Diệt",
            rarity = Rarity.EPIC,
            category = ItemCategory.STAT_KEYWORD,
            description = "Bất Tử",
            grantedKeyword = Keyword.DEATHLESS
        ),
        ItemTemplate(
            id = "item_charging_sigil",
            name = "Ấn Ký Tích Tụ I",
            rarity = Rarity.COMMON,
            category = ItemCategory.ECONOMY,
            description = "-1 Tiêu Hao",
            costReduction = 1
        ),
        ItemTemplate(
            id = "item_nomads_medallion",
            name = "Mề Đay Du Mục",
            rarity = Rarity.EPIC,
            category = ItemCategory.ECONOMY,
            description = "-2 Tiêu Hao",
            costReduction = 2
        ),
        ItemTemplate(
            id = "item_farsight",
            name = "Thấu Thị Biến Đổi",
            rarity = Rarity.RARE,
            category = ItemCategory.TRIGGER,
            description = "Bắt Đầu Trận Đấu: Rút ta nếu chưa có trên tay.",
            farsightGameStartDraw = true
        ),
        ItemTemplate(
            id = "item_shadow_totem",
            name = "Vật Tổ Bóng Tối",
            rarity = Rarity.EPIC,
            category = ItemCategory.TRIGGER,
            description = "Triệu Hồi: Triệu hồi 1 bản sao Bóng Ma của ta.",
            onSummonEphemeralCopy = true
        ),
        ItemTemplate(
            id = "item_hextech_fabricator",
            name = "Máy Chế Tạo Hextech",
            rarity = Rarity.RARE,
            category = ItemCategory.TRIGGER,
            description = "Triệu Hồi: Ban cho ta 1 từ khóa có lợi ngẫu nhiên và Tinh Linh.",
            spiritStacks = 1,
            grantRandomKeywordOnSummon = true
        ),
        ItemTemplate(
            id = "item_mana_gem",
            name = "Nước Mắt Nữ Thần",
            rarity = Rarity.RARE,
            category = ItemCategory.ECONOMY,
            description = "-1 Tiêu Hao và Xuất Trận: Rút 1 lá.",
            costReduction = 1,
            onPlayDrawOne = true
        )
    )

    val relics: List<RelicTemplate>
        get() = RelicCatalog.all

    val powers: List<PowerTemplate> = listOf(
        PowerTemplate("pow_sorcery", "Pháp Thuật", Rarity.RARE, "Đầu Vòng Đấu: Hồi đầy Năng Lượng Phép."),
        PowerTemplate("pow_spellslinger", "Xạ Thủ Phép Thuật", Rarity.COMMON, "Bài phép của bạn giảm 1 tiêu hao."),
        PowerTemplate("pow_wild_inspiration", "Cảm Hứng Hoang Dã", Rarity.COMMON, "Các lá bài được tạo ra của bạn giảm 1 tiêu hao."),
        PowerTemplate("pow_raiding_party", "Băng Cướp Kích", Rarity.COMMON, "Khi gây sát thương lên Nhà Chính địch, ban +1|+1 cho bài quân đầu tiên trên tay."),
        PowerTemplate("pow_enfeebling_strike", "Đòn Đánh Suy Nhược", Rarity.COMMON, "Khi gây sát thương lên bài quân địch, giảm Sức Mạnh của nó bằng lượng sát thương đó."),
        PowerTemplate("pow_vitality", "Sinh Lực Dồi Dào", Rarity.RARE, "Bắt Đầu Trận Đấu & Đầu Vòng Đấu: Hồi 2 Máu cho Nhà Chính."),
        PowerTemplate("pow_manaflow", "Dòng Chảy Năng Lượng Hoàn Mỹ", Rarity.LEGENDARY, "Bắt Đầu Trận Đấu: Nhận +2 ngọc năng lượng (tối đa 10)."),
        PowerTemplate("pow_spell_fury", "Cuồng Nộ Pháp Thuật", Rarity.RARE, "Bài phép và Kỹ Năng của bạn gây thêm 1 sát thương.", spellSkillDamageBonus = 1)
    ) + PowerCatalog.epicPowers

    val cards: Map<String, CardTemplate> = listOf(
        // Champions
        CardTemplate(
            id = "card_jinx", name = "Jinx", region = Region.PILTOVER_ZAUN, type = CardType.CHAMPION,
            cost = 4, basePower = 4, baseHealth = 3, keywords = setOf(Keyword.QUICK_ATTACK),
            isChampion = true, levelUpRequirementText = "Hết bài trên tay hoặc dùng 2+ bài phép",
            description = "Tấn Công Nhanh. Thăng Cấp: Gây 4 sát thương lên Nhà Chính địch và tạo Tên Lửa Đạn Đạo Siêu Khủng Khiếp!",
            levelUpPowerBonus = 1, levelUpHealthBonus = 1
        ),
        CardTemplate(
            id = "card_lux", name = "Lux", region = Region.DEMACIA, type = CardType.CHAMPION,
            cost = 5, basePower = 3, baseHealth = 5, keywords = setOf(Keyword.BARRIER),
            isChampion = true, levelUpRequirementText = "Tiêu 6+ Năng Lượng cho bài phép",
            description = "Lá Chắn. Thăng Cấp: Tạo 1 Cầu Vồng Tối Thượng 0 tiêu hao (Nhanh: Gây 4 sát thương và Áp Đảo lên Nhà Chính).",
            levelUpPowerBonus = 1, levelUpHealthBonus = 1
        ),
        CardTemplate(
            id = "card_yasuo", name = "Yasuo", region = Region.IONIA, type = CardType.CHAMPION,
            cost = 4, basePower = 4, baseHealth = 4, keywords = setOf(Keyword.QUICK_ATTACK),
            isChampion = true, levelUpRequirementText = "Làm Choáng 3+ kẻ địch",
            description = "Tấn Công Nhanh. Khi bạn làm Choáng kẻ địch, Yasuo chém nó 2 sát thương (hoặc toàn bộ Sức Mạnh khi Thăng Cấp).",
            levelUpPowerBonus = 1, levelUpHealthBonus = 1
        ),
        CardTemplate(
            id = "card_garen", name = "Garen", region = Region.DEMACIA, type = CardType.CHAMPION,
            cost = 5, basePower = 5, baseHealth = 5, keywords = setOf(Keyword.REGENERATION),
            isChampion = true, levelUpRequirementText = "Ra đòn 2 lần",
            description = "Hồi Phục. Thăng Cấp: Đầu Vòng Đấu: Tập Hợp!",
            levelUpPowerBonus = 1, levelUpHealthBonus = 1
        ),
        CardTemplate(
            id = "card_ashe", name = "Ashe", region = Region.FRELJORD, type = CardType.CHAMPION,
            cost = 4, basePower = 5, baseHealth = 3, keywords = setOf(Keyword.BRASH),
            isChampion = true, levelUpRequirementText = "Đóng Băng 3+ kẻ địch",
            description = "Hùng Hổ. Tấn Công: Đóng Băng kẻ địch mạnh nhất. Kẻ địch có 0 Sức Mạnh không thể chặn.",
            levelUpPowerBonus = 1, levelUpHealthBonus = 1, rarity = Rarity.LEGENDARY
        ),
        CardTemplate(
            id = "card_anivia", name = "Anivia", region = Region.FRELJORD, type = CardType.CHAMPION,
            cost = 6, basePower = 3, baseHealth = 5, keywords = setOf(Keyword.ELEMENTAL),
            isChampion = true, levelUpRequirementText = "Đạt Khai Sáng",
            description = "Nguyên Tố. Tấn Công: Gây 1 (2 khi Thăng Cấp) sát thương lên tất cả kẻ địch và Nhà Chính của đối thủ. Trăng Trối: Hồi sinh và biến đổi ta thành Trứng Anivia.",
            levelUpPowerBonus = 1, levelUpHealthBonus = 1, rarity = Rarity.LEGENDARY
        ),
        // AI Boss Champions & Reinforcement Champions
        CardTemplate(
            id = "card_zed", name = "Zed", region = Region.IONIA, type = CardType.CHAMPION,
            cost = 3, basePower = 3, baseHealth = 2, keywords = setOf(Keyword.QUICK_ATTACK),
            isChampion = true, description = "Tấn Công Nhanh. Tấn Công: Triệu hồi 1 Phân Thân Bóng Tối đang tấn công."
        ),
        CardTemplate(
            id = "card_karma", name = "Karma", region = Region.IONIA, type = CardType.CHAMPION,
            cost = 5, basePower = 4, baseHealth = 4, keywords = setOf(Keyword.ATTUNE),
            isChampion = true, description = "Điều Hòa. Cuối Vòng Đấu: Tạo 1 bài phép ngẫu nhiên. Khai Sáng: Nhân đôi bài phép."
        ),
        CardTemplate(
            id = "card_viktor", name = "Viktor", region = Region.PILTOVER_ZAUN, type = CardType.CHAMPION,
            cost = 4, basePower = 2, baseHealth = 4, keywords = setOf(Keyword.AUGMENT),
            isChampion = true, description = "Nâng Cấp. Đầu Vòng Đấu: Tạo Lõi Công Nghệ Nâng Cấp để nhận 1 từ khóa ngẫu nhiên."
        ),
        CardTemplate(
            id = "card_aurelion_sol", name = "Aurelion Sol", region = Region.TARGON, type = CardType.CHAMPION,
            cost = 8, basePower = 8, baseHealth = 8, keywords = setOf(Keyword.FURY, Keyword.SPELLSHIELD),
            isChampion = true, description = "Cuồng Nộ, Khiên Phép. Đầu Vòng Đấu: Khai Khẩn 1 bài quân Thượng Giới."
        ),

        // Tùy tùng
        CardTemplate(
            id = "card_zaunite_urchin", name = "Đứa Trẻ Thành Zaun", region = Region.PILTOVER_ZAUN, type = CardType.UNIT,
            cost = 1, basePower = 2, baseHealth = 1, keywords = setOf(Keyword.AUGMENT),
            description = "Nâng Cấp. Xuất Trận: Rút 1 lá."
        ),
        CardTemplate(
            id = "card_boomcrew_rookie", name = "Tân Binh Đội Bom", region = Region.PILTOVER_ZAUN, type = CardType.UNIT,
            cost = 2, basePower = 1, baseHealth = 3, keywords = setOf(Keyword.IMPACT), impactStacks = 1,
            description = "Khí Lực 1. Tấn Công: Gây 1 sát thương lên Nhà Chính địch."
        ),
        CardTemplate(
            id = "card_sumpworks_mapper", name = "Poro Liều Lĩnh", region = Region.PILTOVER_ZAUN, type = CardType.UNIT,
            cost = 1, basePower = 1, baseHealth = 1, keywords = setOf(Keyword.ELUSIVE),
            description = "Huyền Ảo.", isPoro = true
        ),
        CardTemplate(
            id = "card_ballistic_bot", name = "Người Máy Đạn Đạo", region = Region.PILTOVER_ZAUN, type = CardType.UNIT,
            cost = 2, basePower = 1, baseHealth = 3, keywords = setOf(Keyword.AUGMENT),
            description = "Nâng Cấp. Đầu Vòng Đấu: Tạo 1 Tia Lửa Chóng Tàn (Siêu Tốc: Gây 1 lên Nhà Chính địch)."
        ),
        CardTemplate(
            id = "card_cithria", name = "Cithria Xứ Cloudfield", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 1, basePower = 2, baseHealth = 2, keywords = setOf(Keyword.TOUGH),
            description = "Kiên Cường. Hộ vệ tiên phong đáng tin cậy."
        ),
        CardTemplate(
            id = "card_brightsteel_protector", name = "Hộ Vệ Thép Sáng", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 2, basePower = 3, baseHealth = 2, keywords = setOf(Keyword.SHARPSIGHT),
            description = "Tinh Mắt. Xuất Trận: Ban Lá Chắn cho đồng minh mạnh nhất trong vòng này."
        ),
        CardTemplate(
            id = "card_laurent_protege", name = "Đệ Tử Gia Tộc Laurent", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 3, basePower = 2, baseHealth = 4, keywords = setOf(Keyword.CHALLENGER),
            description = "Thách Đấu. Kéo mục tiêu nguy hiểm của địch vào giao tranh."
        ),
        CardTemplate(
            id = "card_radiant_guardian", name = "Vệ Binh Ánh Sáng", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 5, basePower = 4, baseHealth = 5, keywords = setOf(Keyword.TOUGH, Keyword.LIFESTEAL),
            description = "Kiên Cường, Hút Máu. Hồi máu cho Nhà Chính mỗi lần ra đòn."
        ),
        CardTemplate(
            id = "card_greenfang_warden", name = "Vệ Binh Nanh Xanh", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 3, basePower = 2, baseHealth = 2, keywords = setOf(Keyword.SCOUT, Keyword.BARRIER),
            description = "Trinh Sát, Lá Chắn."
        ),
        CardTemplate(
            id = "card_fae_blader", name = "Vũ Công Kiếm Tiên", region = Region.IONIA, type = CardType.UNIT,
            cost = 2, basePower = 1, baseHealth = 3, keywords = setOf(Keyword.QUICK_ATTACK),
            description = "Tấn Công Nhanh. Nhận +2|+0 mỗi khi bạn làm Choáng kẻ địch."
        ),
        CardTemplate(
            id = "card_kinkou_student", name = "Kinkou Sinh Mệnh Kiếm", region = Region.IONIA, type = CardType.UNIT,
            cost = 4, basePower = 2, baseHealth = 3, keywords = setOf(Keyword.ELUSIVE, Keyword.LIFESTEAL),
            description = "Huyền Ảo, Hút Máu."
        ),
        CardTemplate(
            id = "card_shadow_assassin", name = "Sát Thủ Bóng Đêm", region = Region.IONIA, type = CardType.UNIT,
            cost = 3, basePower = 2, baseHealth = 2, keywords = setOf(Keyword.ELUSIVE),
            description = "Huyền Ảo. Xuất Trận: Rút 1 lá."
        ),
        CardTemplate(
            id = "card_solari_soldier", name = "Khiên Binh Solari", region = Region.TARGON, type = CardType.UNIT,
            cost = 2, basePower = 3, baseHealth = 4, keywords = setOf(Keyword.FATED, Keyword.DAYBREAK),
            description = "Định Mệnh. Hừng Đông: Nhận +1|+1 và Thách Đấu."
        ),
        CardTemplate(
            id = "card_petricite_broadwing", name = "Dực Thạch Kháng Ma", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 2, basePower = 0, baseHealth = 3, keywords = setOf(Keyword.FORMIDABLE, Keyword.CHALLENGER),
            description = "Uy Dũng, Thách Đấu. Ra đòn bằng Máu hiện tại."
        ),
        CardTemplate(
            id = "card_void_abomination", name = "Ấu Trùng Hư Không", region = Region.RUNETERRA, type = CardType.UNIT,
            cost = 3, basePower = 3, baseHealth = 3, keywords = setOf(Keyword.EVOLVE, Keyword.SPIRIT), spiritStacks = 1,
            description = "Tiến Hóa, Tinh Linh 1. Nhận +2|+2 khi đạt 6 từ khóa có lợi khác nhau."
        ),
        CardTemplate(
            id = "card_spectral_ironhound", name = "Bóng Ma Tinh Nghịch", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 3, basePower = 2, baseHealth = 3, keywords = setOf(Keyword.FEARSOME),
            description = "Đe Dọa."
        ),
        CardTemplate(
            id = "card_rekindled_knight", name = "Hiệp Sĩ Bất Diệt", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 4, basePower = 3, baseHealth = 3, keywords = setOf(Keyword.DEATHLESS, Keyword.FURY),
            description = "Bất Tử, Cuồng Nộ."
        ),
        CardTemplate(
            id = "card_icevale_archer", name = "Cung Thủ Thung Lũng Băng", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 2, basePower = 3, baseHealth = 1, keywords = setOf(Keyword.BRASH),
            description = "Hùng Hổ. Xuất Trận: Đóng Băng kẻ địch mạnh nhất.",
            effectType = CardEffectType.FROSTBITE_TARGET, effectValue = 1
        ),
        CardTemplate(
            id = "card_ruthless_raider", name = "Nữ Tặc Tàn Nhẫn", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 2, basePower = 3, baseHealth = 1, keywords = setOf(Keyword.OVERWHELM, Keyword.TOUGH),
            description = "Áp Đảo, Kiên Cường."
        ),
        // Bộ bài Anivia & Các Khổng Lồ Freljord
        CardTemplate(
            id = "card_wild_mystics", name = "Thầy Tu Hoang Dã", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 2, basePower = 2, baseHealth = 2, keywords = setOf(Keyword.OVERWHELM),
            description = "Áp Đảo. Xuất Trận: Tạo 1 lá bài phép Tên Đóng Băng lên tay.",
            rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_mortal_prophet", name = "Tiên Tri Chết Chóc", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 2, basePower = 0, baseHealth = 5, keywords = emptySet(),
            description = "Nếu ta nhận sát thương và sống sót, gây 1 sát thương lên Nhà Chính của đối thủ.",
            rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_guardian_follower", name = "Tùy Tùng Hộ Vệ", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 3, basePower = 4, baseHealth = 5, keywords = setOf(Keyword.CHALLENGER),
            description = "Thách Đấu. Miễn nhiễm sát thương từ bài phép hoặc Kỹ Năng của đồng minh.",
            rarity = Rarity.RARE
        ),
        CardTemplate(
            id = "card_flame_eyed_caprine", name = "Hỏa Nhân", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 4, basePower = 3, baseHealth = 2, keywords = setOf(Keyword.TOUGH),
            description = "Kiên Cường. Xuất Trận: Gây 1 sát thương lên Nhà Chính địch và toàn bộ quân địch.",
            rarity = Rarity.RARE
        ),
        CardTemplate(
            id = "card_crystal_stormbird", name = "Chim Bão", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 5, basePower = 4, baseHealth = 5, keywords = setOf(Keyword.ELEMENTAL),
            description = "Nguyên Tố. Xuất Trận: Kỹ Năng Nguyên Tố gây 2 sát thương lên TẤT CẢ bài quân khác.",
            rarity = Rarity.RARE
        ),
        CardTemplate(
            id = "card_uzgar", name = "Uzgar Cổ Đại", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 8, basePower = 8, baseHealth = 8, keywords = setOf(Keyword.REGENERATION, Keyword.CHALLENGER),
            description = "Hồi Phục, Thách Đấu. Khổng Lồ cổ xưa vùng Freljord.",
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_rhasa", name = "Rhasa Phân Ly", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 8, basePower = 8, baseHealth = 6, keywords = emptySet(),
            description = "Xuất Trận: Tiêu diệt 2 kẻ địch có chỉ số Công thấp nhất trên sân.",
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_rumul", name = "Rumul", region = Region.TARGON, type = CardType.UNIT,
            cost = 8, basePower = 8, baseHealth = 8, keywords = emptySet(),
            description = "Xuất Trận: Ban +0|+4 cho đồng minh có Máu hiện tại cao nhất trên sân.",
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_lost_soul", name = "Linh Hồn Lạc Lối", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 8, basePower = 5, baseHealth = 4, keywords = emptySet(),
            description = "Trăn Trối: Tạo 1 lá Song Kiếm Ác Quỷ lên tay.",
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_twinblade_revenant", name = "Song Kiếm Ác Quỷ", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 4, basePower = 4, baseHealth = 3, keywords = setOf(Keyword.FEARSOME),
            description = "Đáng Sợ. Tạo ra khi Linh Hồn Lạc Lối bị hạ gục.",
            isCollectibleReward = false, rarity = Rarity.RARE
        ),
        CardTemplate(
            id = "card_freljord_titan", name = "Kẻ Lang Thang Vô Định", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 9, basePower = 8, baseHealth = 8, keywords = setOf(Keyword.OVERWHELM, Keyword.TOUGH),
            description = "Áp Đảo, Kiên Cường. Khổng Lồ vùng Freljord.",
            rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_glacial_colossus", name = "Cự Thú Băng Tâm", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 7, basePower = 6, baseHealth = 7, keywords = setOf(Keyword.OVERWHELM),
            description = "Áp Đảo. Khổng Lồ băng hà vĩ đại.",
            rarity = Rarity.RARE
        ),
        CardTemplate(
            id = "card_ancient_yeti", name = "Yeti Cổ Đại", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 7, basePower = 5, baseHealth = 6, keywords = setOf(Keyword.OVERWHELM, Keyword.REGENERATION),
            description = "Áp Đảo, Hồi Phục.",
            rarity = Rarity.RARE
        ),

        // Bài Phép
        CardTemplate(
            id = "card_get_excited", name = "Hưng Phấn Tột Độ!", region = Region.PILTOVER_ZAUN, type = CardType.SPELL,
            cost = 3, spellSpeed = SpellSpeed.FAST, keywords = setOf(Keyword.ELEMENTAL),
            description = "Nhanh. Gây 3 sát thương lên 1 bài quân địch hoặc Nhà Chính địch.",
            effectType = CardEffectType.DEAL_DAMAGE_ANY, effectValue = 3
        ),
        CardTemplate(
            id = "card_mystic_shot", name = "Phát Bắn Thần Bí", region = Region.PILTOVER_ZAUN, type = CardType.SPELL,
            cost = 2, spellSpeed = SpellSpeed.FAST,
            description = "Nhanh. Gây 2 sát thương lên Nhà Chính địch hoặc kẻ địch yếu nhất.",
            effectType = CardEffectType.DEAL_DAMAGE_ANY, effectValue = 2
        ),
        CardTemplate(
            id = "card_super_mega_rocket", name = "Tên Lửa Đạn Đạo Siêu Khủng Khiếp!", region = Region.PILTOVER_ZAUN, type = CardType.SPELL,
            cost = 1, spellSpeed = SpellSpeed.SLOW, keywords = setOf(Keyword.FLEETING),
            description = "Chậm, Chóng Tàn. Gây 4 sát thương lên Nhà Chính địch và 2 lên toàn bộ bài quân địch.",
            effectType = CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS, effectValue = 4,
            isCollectibleReward = false
        ),
        CardTemplate(
            id = "card_prismatic_barrier", name = "Lá Chắn Lăng Kính", region = Region.DEMACIA, type = CardType.SPELL,
            cost = 2, spellSpeed = SpellSpeed.BURST,
            description = "Siêu Tốc. Ban Lá Chắn cho đồng minh mạnh nhất vòng này và rút 1 lá.",
            effectType = CardEffectType.GIVE_BARRIER_AND_DRAW, effectValue = 1
        ),
        CardTemplate(
            id = "card_golden_aegis", name = "Khiên Vàng Tập Hợp", region = Region.DEMACIA, type = CardType.SPELL,
            cost = 4, spellSpeed = SpellSpeed.SLOW,
            description = "Chậm. Ban Lá Chắn cho đồng minh mạnh nhất và Tập Hợp!",
            effectType = CardEffectType.RALLY_AND_SHIELD, effectValue = 1
        ),
        CardTemplate(
            id = "card_final_spark", name = "Cầu Vồng Tối Thượng", region = Region.DEMACIA, type = CardType.SPELL,
            cost = 0, spellSpeed = SpellSpeed.FAST, keywords = setOf(Keyword.OVERWHELM, Keyword.FLEETING),
            description = "Nhanh, Áp Đảo, Chóng Tàn. Gây 4 sát thương lên kẻ địch và sát thương dư lên Nhà Chính.",
            effectType = CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS, effectValue = 4,
            isCollectibleReward = false
        ),
        CardTemplate(
            id = "card_steel_tempest", name = "Bão Thép", region = Region.IONIA, type = CardType.SPELL,
            cost = 2, spellSpeed = SpellSpeed.FAST,
            description = "Nhanh. Làm Choáng kẻ địch mạnh nhất.",
            effectType = CardEffectType.STUN_TARGET, effectValue = 1
        ),
        CardTemplate(
            id = "card_concussive_palm", name = "Bàn Tay Sấm Sét", region = Region.IONIA, type = CardType.SPELL,
            cost = 4, spellSpeed = SpellSpeed.FAST,
            description = "Nhanh. Làm Choáng 1 kẻ địch và triệu hồi Đuôi Rồng 3|2.",
            effectType = CardEffectType.STUN_TARGET, effectValue = 2
        ),
        CardTemplate(
            id = "card_flash_freeze", name = "Đóng Băng Tức Thì", region = Region.FRELJORD, type = CardType.SPELL,
            cost = 3, spellSpeed = SpellSpeed.BURST, keywords = setOf(Keyword.ELEMENTAL),
            description = "Siêu Tốc, Nguyên Tố. Đóng Băng kẻ địch mạnh nhất.",
            effectType = CardEffectType.FROSTBITE_TARGET, effectValue = 1
        ),
        CardTemplate(
            id = "card_single_combat", name = "Đấu Tay Đôi", region = Region.DEMACIA, type = CardType.SPELL,
            cost = 2, spellSpeed = SpellSpeed.FAST,
            description = "Nhanh. Đồng minh mạnh nhất và kẻ địch yếu nhất đánh nhau.",
            effectType = CardEffectType.SINGLE_COMBAT_STRIKE, effectValue = 1
        ),
        CardTemplate(
            id = "card_time_trick", name = "Thủ Thuật Thời Gian", region = Region.PILTOVER_ZAUN, type = CardType.SPELL,
            cost = 2, spellSpeed = SpellSpeed.FOCUS,
            description = "Tập Trung. Tiên Đoán và Rút 1 lá.",
            effectType = CardEffectType.PREDICT_DRAW, effectValue = 1
        ),
        CardTemplate(
            id = "card_bard_chimes", name = "Mạch Lạc Vũ Trụ", region = Region.TARGON, type = CardType.SPELL,
            cost = 2, spellSpeed = SpellSpeed.FOCUS,
            description = "Tập Trung. Gieo 3 Chuông vào bộ bài và ban +1|+1 cho đồng minh.",
            effectType = CardEffectType.PLANT_CHIMES, effectValue = 3
        ),
        CardTemplate(
            id = "card_teemo_puffcaps", name = "Đám Mây Nấm Độc", region = Region.PILTOVER_ZAUN, type = CardType.SPELL,
            cost = 1, spellSpeed = SpellSpeed.BURST,
            description = "Siêu Tốc. Gieo 5 Bẫy Nấm Độc vào bộ bài đối thủ.",
            effectType = CardEffectType.PLANT_PUFFCAPS, effectValue = 5
        ),
        CardTemplate(
            id = "card_harsh_winds", name = "Cuồng Phong", region = Region.FRELJORD, type = CardType.SPELL,
            cost = 5, spellSpeed = SpellSpeed.BURST, keywords = setOf(Keyword.ELEMENTAL),
            description = "Siêu Tốc, Nguyên Tố. Đóng Băng tối đa 2 kẻ địch, sau đó rút 1 lá.",
            effectType = CardEffectType.FROSTBITE_TWO, effectValue = 2,
            rarity = Rarity.RARE
        ),
        CardTemplate(
            id = "card_anivia_harsh_winds", name = "Anivia: Cuồng Phong", region = Region.FRELJORD, type = CardType.SPELL,
            cost = 5, spellSpeed = SpellSpeed.BURST, keywords = setOf(Keyword.ELEMENTAL),
            description = "Siêu Tốc, Nguyên Tố. Đóng Băng tối đa 2 kẻ địch, sau đó tráo 1 Anivia vào bộ bài.",
            effectType = CardEffectType.ANIVIA_CHAMPION_SPELL, effectValue = 2,
            isCollectibleReward = false, rarity = Rarity.LEGENDARY
        ),
        CardTemplate(
            id = "card_frozen_arrow", name = "Tên Đóng Băng", region = Region.FRELJORD, type = CardType.SPELL,
            cost = 2, spellSpeed = SpellSpeed.BURST, keywords = setOf(Keyword.ELEMENTAL),
            description = "Siêu Tốc, Nguyên Tố. Đóng Băng 1 kẻ địch.",
            effectType = CardEffectType.FROSTBITE_TARGET, effectValue = 1,
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_wings_of_cryophoenix", name = "Đôi Cánh Phượng Hoàng Băng", region = Region.FRELJORD, type = CardType.SPELL,
            cost = 7, spellSpeed = SpellSpeed.SLOW, keywords = setOf(Keyword.ELEMENTAL),
            description = "Chậm, Nguyên Tố. Gây 4 sát thương lên TẤT CẢ bài quân trên sân, NGOẠI TRỪ Anh Hùng và Thầy Tu Hoang Dã.",
            effectType = CardEffectType.WINGS_OF_CRYOPHOENIX, effectValue = 4,
            rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_clash_of_giants", name = "Khổng Lồ Chạm Trán", region = Region.FRELJORD, type = CardType.SPELL,
            cost = 13, spellSpeed = SpellSpeed.SLOW, keywords = setOf(Keyword.ELEMENTAL),
            description = "Chậm, Nguyên Tố. Triệu hồi 2 Khổng Lồ ngẫu nhiên. Giảm 1 tiêu hao với mỗi Kỹ Năng hoặc bài phép Nguyên Tố đã dùng (tối đa 10).",
            effectType = CardEffectType.SUMMON_TWO_GIANTS, effectValue = 2,
            rarity = Rarity.EPIC
        ),

        // Trang bị & Địa danh
        CardTemplate(
            id = "card_forged_blade", name = "Kích Darkin", region = Region.DEMACIA, type = CardType.EQUIPMENT,
            cost = 2, basePower = 2, baseHealth = 1, keywords = setOf(Keyword.CHALLENGER),
            description = "Trang Bị Darkin. Ban +2|+1 và Thách Đấu cho đồng minh mạnh nhất.",
            isDarkin = true, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_taarosh", name = "Taarosh", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 10, basePower = 8, baseHealth = 8, keywords = setOf(Keyword.OVERWHELM),
            description = "Darkin, Áp Đảo.", isDarkin = true, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_sun_disc", name = "Đài Quan Sát Hextech", region = Region.PILTOVER_ZAUN, type = CardType.LANDMARK,
            cost = 2, countdownRounds = 2,
            description = "Địa Danh. Đếm Ngược 2: Rút 2 lá và hồi 2 Năng Lượng Phép."
        ),
        CardTemplate(
            id = "card_hextech_observatory", name = "Đài Quan Sát Hextech", region = Region.PILTOVER_ZAUN, type = CardType.LANDMARK,
            cost = 3, countdownRounds = 0,
            description = "Địa Danh. Đầu Vòng Đấu: Nếu bạn có Lượt Tấn Công, rút 1 lá.",
            isCollectibleReward = false, rarity = Rarity.RARE
        ),
        CardTemplate(
            id = "card_buried_sun_disc", name = "Đĩa Mặt Trời Chôn Vùi", region = Region.SHURIMA, type = CardType.LANDMARK,
            cost = 1, countdownRounds = 25,
            description = "Địa Danh. Đếm Ngược 25: Khôi phục Đĩa Mặt Trời và Thăng Cấp các Tướng Shurima. Khi Tướng Shurima Thăng Cấp, đẩy nhanh 10 vòng.",
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_ancient_preparations", name = "Chuẩn Bị Cổ Xưa", region = Region.SHURIMA, type = CardType.LANDMARK,
            cost = 1, countdownRounds = 2,
            description = "Địa Danh. Đếm Ngược 2: Rút 1 lá.",
            rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_hibernating_rockbear", name = "Gấu Đá Ngủ Đông", region = Region.SHURIMA, type = CardType.LANDMARK,
            cost = 2, countdownRounds = 3,
            description = "Địa Danh. Đếm Ngược 3: Triệu hồi 1 Gấu Đá Khổng Lồ (5|4).",
            rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_grumpy_rockbear", name = "Gấu Đá Khổng Lồ", region = Region.SHURIMA, type = CardType.UNIT,
            cost = 5, basePower = 5, baseHealth = 4, keywords = emptySet(),
            description = "Mãnh thú đá cổ đại vùng Shurima.",
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),

        // Bài Lời Nguyền & Token
        CardTemplate(
            id = "card_curse_shackles", name = "Xiềng Xích", region = Region.SHADOW_ISLES, type = CardType.CURSE,
            cost = 2, spellSpeed = SpellSpeed.FOCUS,
            description = "Lời Nguyền. Dùng để giải thoát đồng minh đang bị Xiềng Xích.",
            effectType = CardEffectType.PURGE_SHACKLES_CURSE,
            isCollectibleReward = false
        ),
        CardTemplate(
            id = "card_curse_suppression", name = "Áp Chế", region = Region.DEMACIA, type = CardType.CURSE,
            cost = 2, spellSpeed = SpellSpeed.FOCUS,
            description = "Lời Nguyền. Khi còn trên tay, bài phép của bạn tốn thêm 1 tiêu hao. Dùng để hóa giải.",
            effectType = CardEffectType.PURGE_SUPPRESSION_CURSE,
            isCollectibleReward = false
        ),
        CardTemplate(
            id = "card_curse_terror", name = "Kinh Hoàng", region = Region.SHADOW_ISLES, type = CardType.CURSE,
            cost = 2, spellSpeed = SpellSpeed.FOCUS,
            description = "Lời Nguyền. Giảm -1|-0 lên đồng minh khi còn trên tay. Dùng để hóa giải.",
            effectType = CardEffectType.PURGE_TERROR_CURSE,
            isCollectibleReward = false
        ),
        CardTemplate(
            id = "card_ignition", name = "Tia Lửa", region = Region.PILTOVER_ZAUN, type = CardType.SPELL,
            cost = 0, spellSpeed = SpellSpeed.BURST, keywords = setOf(Keyword.FLEETING),
            description = "Siêu Tốc, Chóng Tàn. Gây 1 sát thương lên Nhà Chính địch.",
            effectType = CardEffectType.DEAL_DAMAGE_NEXUS, effectValue = 1,
            isCollectibleReward = false
        ),
        CardTemplate(
            id = "card_tail_of_the_dragon", name = "Đuôi Rồng", region = Region.IONIA, type = CardType.UNIT,
            cost = 3, basePower = 3, baseHealth = 2, keywords = setOf(Keyword.ATTUNE),
            description = "Điều Hòa. Được triệu hồi từ Bàn Tay Sấm Sét.",
            isCollectibleReward = false
        ),
        CardTemplate(
            id = "card_eggnivia", name = "Trứng Anivia", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 1, basePower = 0, baseHealth = 2, keywords = emptySet(),
            description = "Đầu Vòng Đấu: Nếu đạt Khai Sáng, hồi sinh thành Anivia đúng cấp độ lúc chết với chỉ số gốc.",
            isCollectibleReward = false
        ),
        CardTemplate(
            id = "skill_glacial_storm", name = "Bão Băng Giá", region = Region.FRELJORD, type = CardType.SPELL,
            cost = 0, spellSpeed = SpellSpeed.SKILL, keywords = setOf(Keyword.ELEMENTAL),
            description = "Kỹ Năng Nguyên Tố. Gây sát thương lên tất cả kẻ địch và Nhà Chính địch.",
            effectType = CardEffectType.GLACIAL_STORM_SKILL, effectValue = 1,
            isCollectibleReward = false
        ),
        // --- QUẦN ĐẢO BÓNG ĐÊM (MAP 5★ FIORA & VIEGO) ---
        CardTemplate(
            id = "card_fiora", name = "Fiora", region = Region.DEMACIA, type = CardType.CHAMPION,
            cost = 3, basePower = 9, baseHealth = 9,
            keywords = setOf(Keyword.CHALLENGER),
            description = "Thách Đấu. Khi tiêu diệt được 4 quân địch, lập tức phá hủy Nhà Chính đối phương.",
            isChampion = true,
            levelUpRequirementText = "Tiêu diệt 2 kẻ địch để Thăng Cấp; tiêu diệt 4 kẻ địch để lập tức thắng trận.",
            levelUpPowerBonus = 1, levelUpHealthBonus = 1,
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_laurent_bladekeeper", name = "Người Giữ Gươm Laurent", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 4, basePower = 5, baseHealth = 4, keywords = emptySet(),
            description = "Xuất Trận: Ban +2|+2 cho 1 đồng minh.",
            isCollectibleReward = false, rarity = Rarity.RARE
        ),
        CardTemplate(
            id = "card_badgerbear", name = "Gấu", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 3, basePower = 4, baseHealth = 6, keywords = emptySet(),
            description = "Mãnh thú rừng sâu Demacia.",
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_tiana_crownguard", name = "Tiana", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 8, basePower = 8, baseHealth = 8, keywords = setOf(Keyword.TOUGH),
            description = "Kiên Cường. Khi triệu hồi: Ban Kiên Cường cho cả đội.",
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_tough_poro", name = "Poro Kiên Cường", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 1, basePower = 1, baseHealth = 1, keywords = setOf(Keyword.TOUGH),
            description = "Kiên Cường.", isPoro = true,
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_dawnspeakers", name = "Speaker", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 3, basePower = 1, baseHealth = 4, keywords = emptySet(),
            description = "Kết Thúc Vòng Đấu: Nếu có đồng minh hy sinh trong vòng, ban +1|+1 cho đồng minh mạnh nhất.",
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_fleetfeather_tracker", name = "Đại Bàng Trinh Sát", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 1, basePower = 2, baseHealth = 1, keywords = emptySet(),
            description = "Khi đồng minh được triệu hồi, trao cho ta Thách Đấu nếu ta đang ở trên sân.",
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_viego", name = "Viego", region = Region.SHADOW_ISLES, type = CardType.CHAMPION,
            cost = 6, basePower = 6, baseHealth = 5,
            keywords = setOf(Keyword.FEARSOME),
            description = "Đáng Sợ. Mỗi vòng, lần đầu đồng minh khác hy sinh, triệu hồi 1 Màn Sương Xâm Chiếm. Lv2 Đầu Vòng: Chiếm vĩnh viễn kẻ địch mạnh nhất (hoặc tiêu diệt nếu là Anh Hùng).",
            isChampion = true,
            levelUpRequirementText = "Ta đã thấy các đồng minh hy sinh có tổng cộng 20+ Sức Mạnh.",
            levelUpPowerBonus = 1, levelUpHealthBonus = 1,
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_encroaching_mist", name = "Màn Sương Xâm Chiếm", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 1, basePower = 1, baseHealth = 1,
            keywords = setOf(Keyword.EPHEMERAL),
            description = "Cảm Tử. Khi được triệu hồi: Ban +1|+1 cho Viego và mọi Màn Sương Xâm Chiếm ở mọi nơi.",
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_spiderling", name = "Nhện", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 1, basePower = 1, baseHealth = 1, keywords = emptySet(),
            description = "Nhện con Quần Đảo Bóng Đêm.",
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_hapless_aristocrat", name = "Quý Tộc", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 1, basePower = 2, baseHealth = 1, keywords = emptySet(),
            description = "Khi chết: Triệu hồi 1 Nhện (1/1).",
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_fading_icon", name = "Biểu Tượng Phai Nhạt", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 2, basePower = 3, baseHealth = 1, keywords = emptySet(),
            description = "Khi được triệu hồi: Triệu hồi 1 Nhện (1/1).",
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_camavoran_soldier", name = "Chiến Binh Camavor", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 3, basePower = 4, baseHealth = 4, keywords = emptySet(),
            description = "Khi ra sân: Triệu hồi 1 Màn Sương Xâm Chiếm.",
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_black_spear_custom", name = "Bàn Tay Chết Chóc", region = Region.SHADOW_ISLES, type = CardType.SPELL,
            cost = 3, spellSpeed = SpellSpeed.FAST,
            description = "Chọn 1 đồng minh để hy sinh, sau đó chọn 1 kẻ địch để gây 5 sát thương.",
            effectType = CardEffectType.SACRIFICE_DAMAGE, effectValue = 5,
            isCollectibleReward = false, rarity = Rarity.RARE
        ),
        CardTemplate(
            id = "card_camavoran_dragon", name = "Rồng Camavor", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 4, basePower = 4, baseHealth = 4,
            keywords = setOf(Keyword.OVERWHELM, Keyword.FEARSOME),
            description = "Rồng, Áp Đảo, Đáng Sợ. Phải hy sinh 1 đồng minh để ra sân. Khi tiêu diệt kẻ địch, hút 1 máu từ Nhà Chính đối phương.",
            requiresAllySacrificeOnPlay = true,
            isDragon = true,
            isCollectibleReward = false, rarity = Rarity.RARE
        ),
        CardTemplate(
            id = "card_possession", name = "Chiếm Hữu", region = Region.SHADOW_ISLES, type = CardType.SPELL,
            cost = 5, spellSpeed = SpellSpeed.SLOW,
            description = "Cướp 1 bài quân của đối phương trong vòng này.",
            effectType = CardEffectType.POSSESS_UNIT, effectValue = 0,
            isCollectibleReward = false, rarity = Rarity.RARE
        ),
        CardTemplate(
            id = "card_invasive_hydravine", name = "Quái Cây", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 7, basePower = 9, baseHealth = 8,
            keywords = setOf(Keyword.FEARSOME),
            description = "Đáng Sợ. Khi ra sân hoặc Bắt Đầu Vòng Đấu, triệu hồi 1 Màn Sương Xâm Chiếm.",
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        // --- BỔ SUNG CHO 55 POWER SỬ THI ---
        CardTemplate(
            id = "card_renekton", name = "Renekton", region = Region.SHURIMA, type = CardType.CHAMPION,
            cost = 4, basePower = 4, baseHealth = 4, keywords = setOf(Keyword.OVERWHELM),
            isChampion = true, levelUpRequirementText = "Gây 12+ sát thương",
            description = "Áp Đảo. Tấn Công: Nhận +2|+1 khi Thăng Cấp.",
            levelUpPowerBonus = 1, levelUpHealthBonus = 1,
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_azir", name = "Azir", region = Region.SHURIMA, type = CardType.CHAMPION,
            cost = 3, basePower = 1, baseHealth = 5, keywords = emptySet(),
            isChampion = true, levelUpRequirementText = "Triệu hồi 10+ đồng minh hoặc Địa Danh",
            description = "Khi đồng minh tấn công, triệu hồi 1 Lính Cát (1|1, Cảm Tử) đang tấn công.",
            levelUpPowerBonus = 1, levelUpHealthBonus = 1,
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_nasus", name = "Nasus", region = Region.SHURIMA, type = CardType.CHAMPION,
            cost = 6, basePower = 2, baseHealth = 2, keywords = setOf(Keyword.FEARSOME),
            isChampion = true, levelUpRequirementText = "Đã tiêu diệt kẻ địch (10+ Sức Mạnh)",
            description = "Đáng Sợ. Có +1|+1 với mỗi kẻ địch bạn đã tiêu diệt trong trận. Thăng Cấp: Nhận Khiên Phép.",
            levelUpPowerBonus = 1, levelUpHealthBonus = 1,
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_galio", name = "Galio", region = Region.DEMACIA, type = CardType.CHAMPION,
            cost = 7, basePower = 0, baseHealth = 8, keywords = setOf(Keyword.FORMIDABLE, Keyword.SPELLSHIELD),
            isChampion = true, levelUpRequirementText = "Đồng minh có tổng 25+ Máu",
            description = "Uy Dũng, Khiên Phép. Xuất Trận: Ban +0|+3 cho đồng minh.",
            levelUpPowerBonus = 0, levelUpHealthBonus = 4,
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_sand_soldier", name = "Lính Cát", region = Region.SHURIMA, type = CardType.UNIT,
            cost = 1, basePower = 1, baseHealth = 1, keywords = setOf(Keyword.EPHEMERAL),
            description = "Cảm Tử. Khi tấn công, gây 1 sát thương lên Nhà Chính địch.",
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_sapling", name = "Chồi Non", region = Region.SHADOW_ISLES, type = CardType.UNIT,
            cost = 1, basePower = 2, baseHealth = 1, keywords = setOf(Keyword.CHALLENGER, Keyword.EPHEMERAL),
            description = "Thách Đấu, Phù Du.",
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_caustic_cask", name = "Thùng Thuốc Độc", region = Region.PILTOVER_ZAUN, type = CardType.UNIT,
            cost = 0, basePower = 0, baseHealth = 1, keywords = setOf(Keyword.EPHEMERAL),
            description = "Phù Du. Trăn Trối: Gây 1 sát thương lên cả 2 Nhà Chính.",
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_lonely_poro", name = "Poro Cô Đơn", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 1, basePower = 1, baseHealth = 1, keywords = emptySet(),
            description = "Poro nhỏ bé vùng Freljord.", isPoro = true,
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_nimble_poro", name = "Poro Nhanh Nhẹn", region = Region.IONIA, type = CardType.UNIT,
            cost = 1, basePower = 1, baseHealth = 1, keywords = setOf(Keyword.QUICK_ATTACK),
            description = "Tấn Công Nhanh.", isPoro = true,
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_animated_spell", name = "Phép Thuật Hoạt Hóa", region = Region.RUNETERRA, type = CardType.UNIT,
            cost = 1, basePower = 1, baseHealth = 1, keywords = emptySet(),
            description = "Đơn vị được hoạt hóa từ bài phép.",
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_hex_core_upgrade", name = "Lõi Công Nghệ Nâng Cấp", region = Region.PILTOVER_ZAUN, type = CardType.SPELL,
            cost = 1, spellSpeed = SpellSpeed.BURST,
            description = "Siêu Tốc. Ban 1 từ khóa có lợi ngẫu nhiên cho đồng minh.",
            effectType = CardEffectType.BUFF_ALLIES_STATS, effectValue = 0,
            isCollectibleReward = false, rarity = Rarity.COMMON
        ),
        CardTemplate(
            id = "card_cythria_the_bold", name = "Cithria Hùng Mạnh", region = Region.DEMACIA, type = CardType.UNIT,
            cost = 6, basePower = 6, baseHealth = 6, keywords = setOf(Keyword.FEARSOME),
            description = "Đáng Sợ. Khi tấn công, ban +1|+1 và Đáng Sợ cho đồng minh khác.",
            rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_scarmother_vrynna", name = "Vrynna Mẹ Sẹo", region = Region.FRELJORD, type = CardType.UNIT,
            cost = 6, basePower = 3, baseHealth = 8, keywords = setOf(Keyword.OVERWHELM),
            description = "Áp Đảo. Chiến binh bất khuất phương Bắc.",
            rarity = Rarity.RARE
        ),
        CardTemplate(
            id = "card_windfarer_hatchling", name = "Rồng Gió Non", region = Region.IONIA, type = CardType.UNIT,
            cost = 7, basePower = 4, baseHealth = 2, keywords = setOf(Keyword.ELUSIVE),
            description = "Huyền Ảo. Xuất Trận: Ban +2|+2 cho đồng minh trong vòng này.",
            rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_breakout", name = "Vượt Ngục!", region = Region.RUNETERRA, type = CardType.SPELL,
            cost = 2, spellSpeed = SpellSpeed.FOCUS,
            description = "Tập Trung. Ban +2|+0 và Áp Đảo cho toàn bộ đồng minh trong vòng này.",
            effectType = CardEffectType.BREAKOUT_EFFECT, effectValue = 2,
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_astral_diffusion", name = "Khuếch Tán Tinh Tú", region = Region.TARGON, type = CardType.SPELL,
            cost = 5, spellSpeed = SpellSpeed.SLOW,
            description = "Chậm. Ban +2|+2 và 1 từ khóa ngẫu nhiên cho toàn bộ đồng minh.",
            effectType = CardEffectType.ASTRAL_DIFFUSION_EFFECT, effectValue = 2,
            isCollectibleReward = false, rarity = Rarity.EPIC
        ),
        CardTemplate(
            id = "card_stance_swap", name = "Đổi Thế Võ", region = Region.FRELJORD, type = CardType.SPELL,
            cost = 3, spellSpeed = SpellSpeed.SLOW,
            description = "Chậm. Chọn 1 đồng minh để ban +2|+2 và Áp Đảo.",
            effectType = CardEffectType.STANCE_SWAP_EFFECT, effectValue = 2,
            rarity = Rarity.COMMON
        )
    ).associateBy { it.id }

    fun championSpellIdFor(championCardId: String): String? = when (championCardId) {
        "card_anivia" -> "card_anivia_harsh_winds"
        else -> null
    }

    fun ensureLocalized(context: android.content.Context): Boolean =
        LocalizationRepository.ensureLoaded(context)

    fun localizedKeyword(nameRef: String): LocalizedEntry? =
        LocalizationRepository.findKeywordEntry(nameRef)

    fun localizedTerm(nameRef: String): LocalizedEntry? =
        LocalizationRepository.findVocabEntry(nameRef)

    fun findCardOrNull(id: String): CardTemplate? = cards[id]
    fun findItemOrNull(id: String): ItemTemplate? = items.find { it.id == id }
    fun findPowerOrNull(id: String): PowerTemplate? = powers.find { it.id == id }
    fun findRelicOrNull(id: String): RelicTemplate? = relics.find { it.id == id }

    fun getCard(id: String): CardTemplate = cards[id] ?: run {
        logMissingId("card", id)
        cards.values.first()
    }
    fun getItem(id: String): ItemTemplate = items.find { it.id == id } ?: run {
        logMissingId("item", id)
        items.first()
    }
    fun getPower(id: String): PowerTemplate = powers.find { it.id == id } ?: run {
        logMissingId("power", id)
        powers.first()
    }
    fun getRelic(id: String): RelicTemplate = relics.find { it.id == id } ?: run {
        logMissingId("relic", id)
        relics.first()
    }

    val giantFollowersPool: List<CardTemplate> by lazy {
        listOf("card_uzgar", "card_rhasa", "card_rumul", "card_lost_soul").map { getCard(it) }
    }

    val collectibleRewardPool: List<CardTemplate> by lazy {
        cards.values.filter { it.isCollectibleReward && !it.isChampion }
    }

    val champions: List<ChampionTemplate> = listOf(
        ChampionTemplate(
            id = "champ_anivia",
            name = "Anivia",
            title = "Phượng Hoàng Băng",
            region = Region.FRELJORD,
            cardId = "card_anivia",
            archetypeSummary = "Kiểm Soát • Tăng Ngọc Năng Lượng & Phép Nguyên Tố (Khai Sáng)",
            unlockFragmentCost = 0,
            baseDeck = listOf(
                DeckCardEntry("card_anivia", 2),
                DeckCardEntry("card_wild_mystics", 2),
                DeckCardEntry("card_mortal_prophet", 2),
                DeckCardEntry("card_guardian_follower", 2),
                DeckCardEntry("card_flame_eyed_caprine", 2),
                DeckCardEntry("card_crystal_stormbird", 2),
                DeckCardEntry("card_harsh_winds", 2),
                DeckCardEntry("card_wings_of_cryophoenix", 2),
                DeckCardEntry("card_clash_of_giants", 2)
            ),
            starPowers = listOf(
                StarPowerSpec(2, "Băng Giá Tràn Trề", "+1 Năng Lượng Khởi Điểm. Nếu ngươi được Khai Sáng, thay vì việc ngươi nhận được 1 ngọc năng lượng thì đồng minh sẽ nhận được +1|+1.", 20, starCrystalCost = 2),
                StarPowerSpec(3, "Ân Huệ Mùa Đông II", "Khi ngươi sử dụng 1 Kỹ Năng hoặc bài phép Nguyên Tố, ngươi sẽ nhận được một ngọc năng lượng rỗng.", 40, starCrystalCost = 5),
                StarPowerSpec(4, "Bền Bỉ", "Khi 1 đồng minh nhận sát thương và sống sót, ban +1|+1 cho đồng minh đó.", 40, starCrystalCost = 5),
                StarPowerSpec(5, "Dòng Chảy Năng Lượng", "Bắt Đầu Trận Đấu: Nhận 1 ngọc năng lượng.", 50, starCrystalCost = 10),
                StarPowerSpec(6, "Tường Băng", "Khi kẻ địch tấn công, Đóng Băng bài quân tấn công mạnh nhất. Kẻ địch với sức mạnh bằng 0 nhận gấp đôi sát thương.", 100, novaCrystalCost = 1)
            ),
            constellationNodes = listOf(
                ConstellationMinorNode("anivia_hp", "Trái Tim Chân Băng", "+5 Máu Tối Đa Nhà Chính", 10, gemstoneCost = 5, bonusMaxHp = 5),
                ConstellationMinorNode("anivia_reroll", "Nhãn Quang Bão Tuyết", "+1 Lượt Đổi Lại", 15, gemstoneCost = 5, bonusRerolls = 1),
                ConstellationMinorNode("anivia_gold", "Kho Báu Băng Giá", "+50 Vàng Khởi Điểm", 15, gemstoneCost = 10, bonusStartingGold = 50)
            )
        )
    )

    fun findChampionOrNull(id: String): ChampionTemplate? = champions.find { it.id == id }

    fun getChampion(id: String): ChampionTemplate = champions.find { it.id == id } ?: run {
        logMissingId("champion", id)
        champions.first()
    }

    data class ReinforcementPackage(
        val championId: String,
        val championCardId: String,
        val title: String,
        val subtitle: String,
        val cardIds: List<String>
    )

    val reinforcementPackages = listOf(
        ReinforcementPackage(
            "reinf_zed", "card_zed", "Zed — Chớp Nhoáng Bóng Đêm",
            "Tấn Công Nhanh & Phân thân Huyền Ảo",
            listOf("card_zed", "card_shadow_assassin", "card_spectral_ironhound")
        ),
        ReinforcementPackage(
            "reinf_garen", "card_garen", "Garen — Sức Mạnh Tiên Phong",
            "Hồi Phục, Thách Đấu & Đấu Tay Đôi",
            listOf("card_garen", "card_laurent_protege", "card_single_combat")
        ),
        ReinforcementPackage(
            "reinf_karma", "card_karma", "Karma — Dòng Chảy Ma Thuật",
            "Điều Hòa, Tiên Đoán & Cộng Hưởng Phép",
            listOf("card_karma", "card_time_trick", "card_prismatic_barrier")
        ),
        ReinforcementPackage(
            "reinf_viktor", "card_viktor", "Viktor — Tiến Hóa Huy Hoàng",
            "Nâng Cấp, Tiến Hóa & Từ Khóa Đa Dạng",
            listOf("card_viktor", "card_ballistic_bot", "card_void_abomination")
        ),
        ReinforcementPackage(
            "reinf_ashe", "card_ashe", "Ashe — Cung Băng Giá",
            "Kiểm soát Đóng Băng & Kết liễu Áp Đảo",
            listOf("card_ashe", "card_icevale_archer", "card_flash_freeze")
        ),
        ReinforcementPackage(
            "reinf_anivia", "card_anivia", "Anivia — Phượng Hoàng Băng",
            "Phép Nguyên Tố, Tăng Ngọc & Khổng Lồ Khai Sáng",
            listOf("card_anivia", "card_crystal_stormbird", "card_harsh_winds")
        )
    )
}
