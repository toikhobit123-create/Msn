package com.example.model

object PowerCatalog {

    val epicPowers: List<PowerTemplate> = listOf(
        // 1. Nhóm GAME_START & ON_COMBAT_VICTORY (7 Power)
        PowerTemplate(
            id = "P0067",
            name = "Dòng Chảy Năng Lượng",
            rarity = Rarity.EPIC,
            description = "Bắt Đầu Trận Đấu: Nhận +1 ngọc năng lượng.",
            trigger = PowerTrigger.GAME_START
        ),
        PowerTemplate(
            id = "P0101",
            name = "Xuất Cổng Thần Tốc",
            rarity = Rarity.EPIC,
            description = "Bắt Đầu Trận Đấu: Triệu hồi 1 bài quân 2 tiêu hao từ bộ bài của bạn.",
            trigger = PowerTrigger.GAME_START
        ),
        PowerTemplate(
            id = "P0057",
            name = "Nguồn Năng Lượng Dự Phòng",
            rarity = Rarity.EPIC,
            description = "Bắt Đầu Trận Đấu: Triệu hồi 1 Đài Quan Sát Hextech.",
            trigger = PowerTrigger.GAME_START
        ),
        PowerTemplate(
            id = "P0226",
            name = "Sa Mạc Trường Tồn",
            rarity = Rarity.EPIC,
            description = "Bắt Đầu Trận Đấu: Triệu hồi Đĩa Mặt Trời Chôn Vùi và tạo Renekton, Azir hoặc Nasus lên tay.",
            trigger = PowerTrigger.GAME_START
        ),
        PowerTemplate(
            id = "P1115",
            name = "Ảnh Hưởng Của Taarosh",
            rarity = Rarity.EPIC,
            description = "Bắt Đầu Trận Đấu: Tạo 1 Kích Darkin lên tay và ban cho nó 1 trang bị ngẫu nhiên.",
            trigger = PowerTrigger.GAME_START
        ),
        PowerTemplate(
            id = "P1122",
            name = "Vượt Ngục!",
            rarity = Rarity.EPIC,
            description = "Bắt Đầu Trận Đấu: Tạo 1 lá Vượt Ngục! lên tay.",
            trigger = PowerTrigger.GAME_START
        ),
        PowerTemplate(
            id = "P0073",
            name = "Gia Tài Vô Tận",
            rarity = Rarity.EPIC,
            description = "Nhận thêm +200 Vàng sau mỗi trận thắng.",
            trigger = PowerTrigger.ON_COMBAT_VICTORY
        ),

        // 2. Nhóm ROUND_START & ON_ATTACK_TOKEN_GAINED (13 Power)
        PowerTemplate(
            id = "P0112",
            name = "Thống Trị",
            rarity = Rarity.EPIC,
            description = "Đầu Vòng Đấu: Tập Hợp.",
            trigger = PowerTrigger.ROUND_START
        ),
        PowerTemplate(
            id = "P0064",
            name = "Đứng Yên Đó!",
            rarity = Rarity.EPIC,
            description = "Đầu Vòng Đấu: Làm Choáng kẻ địch mạnh nhất.",
            trigger = PowerTrigger.ROUND_START
        ),
        PowerTemplate(
            id = "P0050",
            name = "Khoan Đã Nào",
            rarity = Rarity.EPIC,
            description = "Đầu Vòng Đấu: Làm Choáng đơn vị mạnh nhất và đơn vị yếu nhất trên bàn.",
            trigger = PowerTrigger.ROUND_START
        ),
        PowerTemplate(
            id = "P0109",
            name = "Những Người Bạn Nhỏ",
            rarity = Rarity.EPIC,
            description = "Đầu Vòng Đấu: Triệu hồi 1 Poro 1 tiêu hao ngẫu nhiên.",
            trigger = PowerTrigger.ROUND_START
        ),
        PowerTemplate(
            id = "P0045",
            name = "Nguồn Cung Ổn Định",
            rarity = Rarity.EPIC,
            description = "Đầu Vòng Đấu: Triệu hồi 1 Thùng Thuốc Độc.",
            trigger = PowerTrigger.ROUND_START
        ),
        PowerTemplate(
            id = "P0043",
            name = "Luật Nhà",
            rarity = Rarity.EPIC,
            description = "Đầu Vòng Đấu: Ban 1 trang bị ngẫu nhiên cho 1 lá bài ngẫu nhiên trên tay.",
            trigger = PowerTrigger.ROUND_START
        ),
        PowerTemplate(
            id = "P1007",
            name = "Mệnh Lệnh Biệt Kích",
            rarity = Rarity.EPIC,
            description = "Đầu Vòng Đấu: Nếu bạn có Lượt Tấn Công, ban Trinh Sát cho 1 đồng minh ngẫu nhiên.",
            trigger = PowerTrigger.ROUND_START
        ),
        PowerTemplate(
            id = "P0558",
            name = "Sương Mù Dịch Chuyển",
            rarity = Rarity.EPIC,
            description = "Đầu Vòng Đấu: Nếu bạn có Lượt Tấn Công, triệu hồi 1 Màn Sương Xâm Chiếm kèm trang bị và ban +1|+1 cho Màn Sương Xâm Chiếm ở mọi nơi.",
            trigger = PowerTrigger.ROUND_START
        ),
        PowerTemplate(
            id = "P0555",
            name = "Bản Chất Biến Ảo",
            rarity = Rarity.EPIC,
            description = "Đầu Vòng Đấu: Tạo trên tay 1 bài phép Phù Du giá gốc từ 6 trở xuống thuộc khu vực bộ bài. Nó có giá 1 tiêu hao.",
            trigger = PowerTrigger.ROUND_START
        ),
        PowerTemplate(
            id = "P0933",
            name = "Tinh Tú Hồi Đáp",
            rarity = Rarity.EPIC,
            description = "Đầu Vòng Đấu: Tạo 1 Khuếch Tán Tinh Tú lên tay hoặc giảm 2 tiêu hao nếu đã có trên tay.",
            trigger = PowerTrigger.ROUND_START
        ),
        PowerTemplate(
            id = "P0232",
            name = "Cơn Thịnh Nộ Thể Thăng Hoa",
            rarity = Rarity.EPIC,
            description = "Đầu Vòng Đấu: Tạo ngẫu nhiên 1 Địa Danh Đếm Ngược Phù Du lên tay. Khi bạn triệu hồi Địa Danh Đếm Ngược, đẩy nhanh 2 vòng.",
            trigger = PowerTrigger.ROUND_START
        ),
        PowerTemplate(
            id = "P0750",
            name = "Úm Ba La Biến!",
            rarity = Rarity.EPIC,
            description = "Đầu Vòng Đấu: Biến đổi các đơn vị của bạn thành tùy tùng ngẫu nhiên có giá gốc cao hơn 3.",
            trigger = PowerTrigger.ROUND_START
        ),
        PowerTemplate(
            id = "P0119",
            name = "Sự Báo Thù Của Thiên Nhiên",
            rarity = Rarity.EPIC,
            description = "Khi bạn nhận Lượt Tấn Công, triệu hồi 1 Chồi Non (2|1, Thách Đấu, Phù Du).",
            trigger = PowerTrigger.ON_ATTACK_TOKEN_GAINED
        ),

        // 3. Nhóm ROUND_END (4 Power)
        PowerTemplate(
            id = "P0102",
            name = "Giảm Giá Chợ Đen",
            rarity = Rarity.EPIC,
            description = "Cuối Vòng Đấu: Giảm 1 tiêu hao của lá bài đắt nhất trên tay bạn.",
            trigger = PowerTrigger.ROUND_END
        ),
        PowerTemplate(
            id = "P0916",
            name = "Chiếc Hộp Trang Bị Pandora",
            rarity = Rarity.EPIC,
            description = "Cuối Vòng Đấu: Gắn 1 trang bị Thường ngẫu nhiên cho 1 lá bài ngẫu nhiên trên tay (tối đa 3 mỗi lá).",
            trigger = PowerTrigger.ROUND_END
        ),
        PowerTemplate(
            id = "P0938",
            name = "Bàn Cờ Pandora",
            rarity = Rarity.EPIC,
            description = "Cuối Vòng Đấu: Biến đổi mỗi đồng minh thành 1 bài quân ngẫu nhiên có giá gốc cao hơn 2.",
            trigger = PowerTrigger.ROUND_END
        ),
        PowerTemplate(
            id = "P0044",
            name = "An Tử",
            rarity = Rarity.EPIC,
            description = "Cuối Vòng Đấu: Tiêu diệt TẤT CẢ bài quân đang bị thương.",
            trigger = PowerTrigger.ROUND_END
        ),

        // 4. Nhóm ON_SUMMON & ON_PLAY_UNIT (9 Power)
        PowerTemplate(
            id = "P0115",
            name = "Sẻ Chia Là Quan Tâm",
            rarity = Rarity.EPIC,
            description = "Khi bạn triệu hồi 1 đồng minh, ban các từ khóa có lợi của nó cho toàn bộ đồng minh.",
            trigger = PowerTrigger.ON_SUMMON_ALLY
        ),
        PowerTemplate(
            id = "P0063",
            name = "Màn Chào Sân Bùng Nổ",
            rarity = Rarity.EPIC,
            description = "Khi bạn triệu hồi 1 đồng minh, gây 1 sát thương lên Nhà Chính địch.",
            trigger = PowerTrigger.ON_SUMMON_ALLY
        ),
        PowerTemplate(
            id = "P0068",
            name = "Thiên Tài Của Yipp",
            rarity = Rarity.EPIC,
            description = "Khi bạn triệu hồi 1 đồng minh 1 tiêu hao, ban cho nó +2|+2.",
            trigger = PowerTrigger.ON_SUMMON_ALLY
        ),
        PowerTemplate(
            id = "P0375",
            name = "Kho Bụi Phép",
            rarity = Rarity.EPIC,
            description = "Khi bạn triệu hồi 1 đồng minh được tạo ra, ban cho nó +2|+2.",
            trigger = PowerTrigger.ON_SUMMON_ALLY
        ),
        PowerTemplate(
            id = "P0350",
            name = "Sáu Muỗng Đường",
            rarity = Rarity.EPIC,
            description = "Khi bạn triệu hồi 1 đồng minh được tạo ra, ban cho nó +3|+1.",
            trigger = PowerTrigger.ON_SUMMON_ALLY
        ),
        PowerTemplate(
            id = "P0451",
            name = "Quảng Trường Vĩ Đại Hơn",
            rarity = Rarity.EPIC,
            description = "Khi 1 đồng minh được triệu hồi, ban cho nó +2|+2, Thách Đấu và 1 từ khóa ngẫu nhiên trong vòng này.",
            trigger = PowerTrigger.ON_SUMMON_ALLY
        ),
        PowerTemplate(
            id = "P0385",
            name = "Dòng Thời Gian Hội Tụ",
            rarity = Rarity.EPIC,
            description = "Lần đầu tiên bạn dùng 1 tùy tùng mỗi vòng, chọn 1 trong 3 tùy tùng có tiêu hao cao hơn 3 để biến đổi nó thành lá đó.",
            trigger = PowerTrigger.ON_PLAY_UNIT
        ),
        PowerTemplate(
            id = "P0897",
            name = "Thanh Kiếm Darkin",
            rarity = Rarity.EPIC,
            description = "Khi bạn đánh ra hoặc biến đổi thành 1 Darkin, nó ra đòn vào kẻ địch yếu nhất.",
            trigger = PowerTrigger.ON_PLAY_UNIT
        ),
        PowerTemplate(
            id = "P1010",
            name = "Nhịp Điệu Tuyệt Vọng",
            rarity = Rarity.EPIC,
            description = "Đồng minh có \"Màn Đêm: Yểm 2 U Ám lên kẻ địch yếu nhất.\"",
            trigger = PowerTrigger.ON_PLAY_UNIT
        ),

        // 5. Nhóm ON_SPELL_CAST / CREATED_CARD / TRAPS (10 Power)
        PowerTemplate(
            id = "P0326",
            name = "Ngọn Gió Nguyên Tố",
            rarity = Rarity.EPIC,
            description = "Bài phép và Kỹ Năng của bạn gây thêm 2 sát thương.",
            spellSkillDamageBonus = 2,
            trigger = PowerTrigger.ON_PLAY_SPELL
        ),
        PowerTemplate(
            id = "P0118",
            name = "Chia Sẻ Chiến Lợi Phẩm",
            rarity = Rarity.EPIC,
            description = "Khi bạn chọn 1 đồng minh làm mục tiêu bằng bài phép đơn mục tiêu, sao chép bài phép đó lên đồng minh yếu nhất khác.",
            trigger = PowerTrigger.ON_PLAY_SPELL
        ),
        PowerTemplate(
            id = "P0888",
            name = "Bài Học Thứ Hai",
            rarity = Rarity.EPIC,
            description = "Khi bạn dùng 1 bài phép chỉ nhắm vào đồng minh, hoạt hóa (triệu hồi) lá bài phép đó.",
            trigger = PowerTrigger.ON_PLAY_SPELL
        ),
        PowerTemplate(
            id = "P1006",
            name = "Năng Lượng Tái Tạo",
            rarity = Rarity.EPIC,
            description = "Mỗi vòng, lần đầu bạn dùng bài phép từ 6 tiêu hao trở lên, hồi lại Năng Lượng bằng chi phí của nó.",
            trigger = PowerTrigger.ON_PLAY_SPELL
        ),
        PowerTemplate(
            id = "P0047",
            name = "Giấy Phép Hội Pháp Sư",
            rarity = Rarity.EPIC,
            description = "Bài phép của bạn có Phù Du. Khi bạn dùng bài phép không phải Cầu Vồng Tối Thượng, tạo 1 Cầu Vồng Tối Thượng lên tay.",
            trigger = PowerTrigger.ON_PLAY_SPELL
        ),
        PowerTemplate(
            id = "P0997",
            name = "Mề Đay Hồi Sinh",
            rarity = Rarity.EPIC,
            description = "Khi bạn dùng Đổi Thế Võ, hồi 2 Máu Nhà Chính và hồi 1 Năng Lượng Phép.",
            trigger = PowerTrigger.ON_PLAY_SPELL
        ),
        PowerTemplate(
            id = "P0329",
            name = "Ánh Hào Quang Tinh Tú",
            rarity = Rarity.EPIC,
            description = "Khi bạn dùng 1 lá bài được tạo ra, giảm 1 tiêu hao cho Tướng đồng minh ở mọi nơi. Khi bạn đánh ra 1 Tướng, Thăng Cấp cho Tướng đó.",
            trigger = PowerTrigger.ON_PLAY_CREATED_CARD
        ),
        PowerTemplate(
            id = "P0752",
            name = "Lời Nguyền Toàn Tri",
            rarity = Rarity.EPIC,
            description = "Sao chép lá bài đầu tiên bạn dùng mỗi vòng. Lá đó tốn Máu Nhà Chính thay vì Năng Lượng.",
            trigger = PowerTrigger.ON_PLAY_SPELL
        ),
        PowerTemplate(
            id = "P0756",
            name = "Cấm Gian Lận",
            rarity = Rarity.EPIC,
            description = "Mỗi vòng, lần đầu Đối Thủ tạo 1 lá bài lên tay, bạn đánh cắp lá bài đó với giá 0 tiêu hao và Phù Du.",
            trigger = PowerTrigger.ON_ENEMY_CREATE_CARD
        ),
        PowerTemplate(
            id = "P0642",
            name = "Ta Lấy Cả Cái Đó",
            rarity = Rarity.EPIC,
            description = "Khi 1 bẫy của bạn kích hoạt, tạo 1 bản sao Phù Du chính xác của lá bài dính bẫy lên tay và giảm tiêu hao bằng 2 lần số bẫy trên lá đó.",
            trigger = PowerTrigger.ON_TRAP_TRIGGERED
        ),

        // 6. Nhóm ON_DEATH, LEVEL_UP & PASSIVE_AURA (12 Power)
        PowerTemplate(
            id = "P0378",
            name = "Kế Thừa",
            rarity = Rarity.EPIC,
            description = "Khi 1 đồng minh hy sinh, ban Sức Mạnh của nó cho 1 bài quân trên tay.",
            trigger = PowerTrigger.ON_ALLY_DEATH
        ),
        PowerTemplate(
            id = "P1011",
            name = "Cái Giá Của Sự Trả Thù",
            rarity = Rarity.EPIC,
            description = "Khi 1 đồng minh hy sinh, gây 1 sát thương lên Nhà Chính địch.",
            trigger = PowerTrigger.ON_ALLY_DEATH
        ),
        PowerTemplate(
            id = "P0671",
            name = "Những Người Bảo Hộ Demacia",
            rarity = Rarity.EPIC,
            description = "Khi bạn Thăng Cấp 1 Tướng đồng minh, triệu hồi Lux. Nếu đã có Lux, triệu hồi Garen. Nếu có cả hai mà chưa có Galio, triệu hồi Galio.",
            trigger = PowerTrigger.ON_CHAMPION_LEVEL_UP
        ),
        PowerTemplate(
            id = "P0093",
            name = "Không Thể Ngừng Lại",
            rarity = Rarity.EPIC,
            description = "Các hiệu ứng tăng chỉ số hoặc từ khóa \"trong vòng này\" của đồng minh (trừ Lá Chắn) sẽ tồn tại vĩnh viễn.",
            trigger = PowerTrigger.PASSIVE_AURA
        ),
        PowerTemplate(
            id = "P0549",
            name = "Chi Tiêu Quốc Phòng",
            rarity = Rarity.EPIC,
            description = "Nhà Chính của bạn có Kiên Cường.",
            trigger = PowerTrigger.PASSIVE_AURA
        ),
        PowerTemplate(
            id = "P0038",
            name = "Sức Mạnh Không Thể Cản Phá",
            rarity = Rarity.EPIC,
            description = "Đồng minh có +3|+3, Áp Đảo và Không Thể Chặn Đòn.",
            trigger = PowerTrigger.PASSIVE_AURA
        ),
        PowerTemplate(
            id = "P0035",
            name = "Giao Tranh Điên Cuồng",
            rarity = Rarity.EPIC,
            description = "Đồng minh có +2|+2. Cuối Vòng Đấu: Gây 2 sát thương lên toàn bộ đồng minh.",
            trigger = PowerTrigger.PASSIVE_AURA
        ),
        PowerTemplate(
            id = "P0048",
            name = "Cái Giá Của Tiến Bộ",
            rarity = Rarity.EPIC,
            description = "Bài phép của bạn giảm 2 tiêu hao. Bài quân của bạn tăng 1 tiêu hao.",
            trigger = PowerTrigger.PASSIVE_AURA
        ),
        PowerTemplate(
            id = "P0680",
            name = "Những Cuộc Phiêu Lưu Sử Thi",
            rarity = Rarity.EPIC,
            description = "Các lá bài Sử Thi của bạn giảm 2 tiêu hao.",
            trigger = PowerTrigger.PASSIVE_AURA
        ),
        PowerTemplate(
            id = "P0222",
            name = "Áp Đảo Quân Số",
            rarity = Rarity.EPIC,
            description = "Lá bài đầu tiên bạn dùng mỗi vòng giảm 1 tiêu hao với mỗi 20 lá bài khởi điểm trong bộ bài.",
            trigger = PowerTrigger.PASSIVE_AURA
        ),
        PowerTemplate(
            id = "P0914",
            name = "Cấy Ghép Điều Khiển Học",
            rarity = Rarity.EPIC,
            description = "Đồng minh có +1|+1 cho mỗi trang bị gắn trên chúng (tối đa 3).",
            trigger = PowerTrigger.PASSIVE_AURA
        ),
        PowerTemplate(
            id = "P0699",
            name = "Khiên Ánh Dương",
            rarity = Rarity.EPIC,
            description = "Đồng minh có Hừng Đông có Kiên Cường. Khi 1 đồng minh kích hoạt Hừng Đông, ban +0|+1 cho toàn bộ đồng minh.",
            trigger = PowerTrigger.PASSIVE_AURA
        )
    )
}
