package com.trendist.seed;

import java.util.List;
import java.util.Map;

/**
 * 영어판 초기 내용(GLOBAL 나라). 한국어 시드(DataSeeder, GuideSeed)와 같은 키로 짝을 짓는다:
 * 칭호는 한국어 이름, 효과 종류·효과는 코드, 가이드 카테고리는 code, 가이드 글은 (카테고리 code, 한국어 제목).
 * 게임 용어는 프론트 영어 사전과 맞춘다: keyword, orb, stash(보관함), trend score(유행도), combo flame, safe zone, dash.
 */
final class EnglishSeed {
    private EnglishSeed() {}

    record Title(String nameEn, String descriptionEn) {}

    /** 칭호 61개: 한국어 이름 → 영어 이름·설명 */
    static final Map<String, Title> TITLES = Map.ofEntries(
            t("트렌드 워처", "Trend Watcher", "Added your first keyword"),
            t("트렌드 탐험가", "Trend Explorer", "Added 30 keywords"),
            t("트렌드 수집가", "Trend Collector", "Added 100 keywords"),
            t("트렌드 사냥꾼", "Trend Hunter", "Added 500 keywords"),
            t("트렌드 장인", "Trend Artisan", "Added 2,000 keywords"),
            t("트렌드 전설", "Trend Legend", "Added 10,000 keywords"),
            t("완전체", "Fully Grown", "Your icon is fully grown (level 10)"),
            t("스타일리스트", "Stylist", "Customization unlocked (level 10)"),
            t("일취월장", "Rising Fast", "Reached level 20"),
            t("베테랑", "Veteran", "Reached level 30"),
            t("마스터", "Master", "Reached level 50"),
            t("그랜드마스터", "Grandmaster", "Reached level 70"),
            t("레전드", "Legend", "Reached level 100"),
            t("키워드 새싹", "Keyword Sprout", "Held 5 keywords at once"),
            t("알뜰 수집가", "Thrifty Collector", "Held 10 keywords at once"),
            t("멀티태스커", "Multitasker", "Held 20 keywords at once"),
            t("키워드 부자", "Keyword Rich", "Held 30 keywords at once"),
            t("키워드 달인", "Keyword Pro", "Held 40 keywords at once"),
            t("키워드 재벌", "Keyword Tycoon", "Held 50 keywords at once"),
            t("키워드 거물", "Keyword Mogul", "Held 70 keywords at once"),
            t("키워드 제왕", "Keyword Emperor", "Held 100 keywords at once"),
            t("키워드 신화", "Keyword Myth", "Held 200 keywords at once"),
            t("떠오르는 별", "Rising Star", "Your trend score reached 500"),
            t("큰손", "High Roller", "Your trend score reached 1,000"),
            t("슈퍼스타", "Superstar", "Your trend score reached 1,500"),
            t("거인", "Giant", "Reached max size (trend score 2,000)"),
            t("핫스타", "Hot Star", "Made the trend score TOP 10 (with 10 or more players online)"),
            t("시상대", "Podium", "Made the trend score TOP 3 (with 10 or more players online)"),
            t("정상", "Summit", "Reached #1 in trend score (with 10 or more players online)"),
            t("첫 마디", "First Words", "Earned 1 comment point"),
            t("해설 새싹", "Commentary Sprout", "Earned 10 comment points"),
            t("친절한 해설가", "Friendly Commentator", "Earned 100 comment points"),
            t("해설 대장", "Commentary Chief", "Earned 500 comment points"),
            t("호기심 많은", "Curious", "Searched for keywords 10 times"),
            t("탐색가", "Seeker", "Searched for keywords 100 times"),
            t("정보통", "Insider", "Searched for keywords 1,000 times"),
            t("첫 승리", "First Win", "Stole a keyword for the first time"),
            t("사냥꾼의 본능", "Hunter's Instinct", "Stole keywords 50 times"),
            t("아레나의 포식자", "Arena Predator", "Stole keywords 300 times"),
            t("봇 사냥 입문", "Bot Hunting 101", "Beat a bot 10 times"),
            t("봇 사냥꾼", "Bot Hunter", "Beat a bot 100 times"),
            t("기계 파괴자", "Machine Breaker", "Beat a bot 500 times"),
            t("첫 처치", "First Blood", "Took down an opponent for the first time"),
            t("해결사", "Closer", "Took down opponents 10 times"),
            t("저승사자", "Reaper", "Took down opponents 50 times"),
            t("아레나의 지배자", "Arena Overlord", "Took down opponents 200 times"),
            t("최후의 승자", "Last One Standing", "Took down opponents 1,000 times"),
            t("첫 패배", "First Defeat", "Died for the first time"),
            t("칠전팔기", "Never Give Up", "Got back up after dying 10 times"),
            t("백전노장", "Battle-Hardened", "Died 50 times but keep challenging"),
            t("불사조", "Phoenix", "Fell 200 times and rose again"),
            t("끝없는 도전자", "Endless Challenger", "Kept going after dying 1,000 times"),
            t("도토리 키재기", "Acorn Contest", "Bumped into equally matched opponents 10 times"),
            t("연속 타격", "Combo Starter", "Reached a combo of 3"),
            t("불꽃 점화", "Ignition", "Reached a combo of 5 and lit the flame"),
            t("연쇄 반응", "Chain Reaction", "Reached a combo of 10"),
            t("폭주 기관차", "Runaway Train", "Reached a combo of 20"),
            t("멈추지 않는 불꽃", "Unstoppable Flame", "Reached a combo of 40"),
            t("100 콤보", "100 Combo", "Reached a combo of 100"),
            t("보스 사냥 입문", "Boss Hunt Rookie", "Took part in defeating a boss for the first time"),
            t("보스 사냥꾼", "Boss Hunter", "Took part in defeating bosses 5 times"),
            t("보스 슬레이어", "Boss Slayer", "Took part in defeating bosses 25 times"),
            t("보스 정복자", "Boss Conqueror", "Took part in defeating bosses 100 times"),
            t("불 끄미", "Flame Snuffer", "Put out an opponent's flame for the first time"),
            t("소방관", "Firefighter", "Put out opponents' flames 10 times"),
            t("화재 진압 대장", "Fire Chief", "Put out opponents' flames 50 times"),
            t("행운 탐색자", "Lucky Seeker", "Ate 10 orbs"),
            t("구슬 수집가", "Orb Collector", "Ate 100 orbs"),
            t("구슬 마니아", "Orb Maniac", "Ate 1,000 orbs"),
            t("오뚝이", "Comeback Kid", "Lost 50 keywords and got back up"),
            t("불굴", "Unyielding", "Lost 300 keywords and never gave up"),
            t("새내기", "Newcomer", "Collected keywords on 3 different days"),
            t("단골", "Regular", "Collected keywords on 7 different days"),
            t("터줏대감", "Old-Timer", "Collected keywords on 30 different days"),
            t("산증인", "Living Witness", "Collected keywords on 100 different days"));

    private static Map.Entry<String, Title> t(String ko, String nameEn, String descriptionEn) {
        return Map.entry(ko, new Title(nameEn, descriptionEn));
    }

    /** 효과 종류 코드 → 영어 이름 */
    static final Map<String, String> EFFECT_TYPES = Map.of("TRAIL", "Trail effect", "BORDER", "Border effect");

    /** 효과: "종류코드/효과코드" → 영어 이름 */
    static final Map<String, String> EFFECTS = Map.ofEntries(
            Map.entry("TRAIL/afterimage", "Afterimage"),
            Map.entry("TRAIL/sparkle", "Stardust"),
            Map.entry("TRAIL/flame", "Flame"),
            Map.entry("TRAIL/bubble", "Bubbles"),
            Map.entry("TRAIL/rainbow", "Rainbow"),
            Map.entry("TRAIL/footprint", "Footprints"),
            Map.entry("TRAIL/petal", "Petals"),
            Map.entry("TRAIL/heart", "Hearts"),
            Map.entry("TRAIL/note", "Music notes"),
            Map.entry("TRAIL/bolt", "Lightning"),
            Map.entry("TRAIL/smoke", "Smoke"),
            Map.entry("TRAIL/snow", "Snowflakes"),
            Map.entry("TRAIL/pawprint", "Cat and dog paws"),
            Map.entry("TRAIL/birdprint", "Bird tracks"),
            Map.entry("TRAIL/bearprint", "Bear prints"),
            Map.entry("TRAIL/rabbitprint", "Rabbit prints"),
            Map.entry("BORDER/glow", "Soft glow"),
            Map.entry("BORDER/dash", "Rotating dashes"),
            Map.entry("BORDER/gradient", "Gradient"),
            Map.entry("BORDER/pulse", "Pulse"),
            Map.entry("BORDER/rainbow", "Rainbow"));

    record Article(String titleEn, String bodyEn) {}

    record Category(String code, String nameEn, List<Article> articles) {}

    /** 가이드. 카테고리와 글의 순서는 GuideSeed.CATEGORIES와 같다(글은 순서로 한국어 제목과 짝을 짓는다) */
    static final List<Category> GUIDE = List.of(
            new Category("getting-started", "🚀 Getting started", List.of(
                    new Article("What kind of game is Trendist?", """
                            It's a real-time arena game where you find the **keywords** that are trending right now before anyone else, add them to your stash, and watch your orb grow.

                            Trends change every day. Sensing what people are searching for right now is the real skill.

                            [[시작 3단계]]"""),
                    new Article("Getting started", """
                            1. Press **Start** on the first screen to jump in without signing in.
                            2. Pick a nickname and you'll spawn in the blue **safe zone** in the middle of the arena.
                            3. Use **Search** on the right to find a hot word and **Add** it.

                            > Sign in with Google to continue with the same progress on other devices. Your guest progress carries over too."""),
                    new Article("Moving around", """
                            [[controls]]

                            - **PC mouse**: **Right-click** to go there (hold to keep following). **Left-click** another orb (player or bot) to follow it automatically.
                            - **Arrow keys / WASD**: Move in the direction you press. Pressing them while following switches to manual control.
                            - **Mobile joystick**: Press and drag anywhere to move that way; drag less to go slower. Tap an orb to follow it automatically.
                            - **⚡ Dash**: `Space` on PC, the ⚡ button at the bottom on mobile. You move twice as fast for 3 seconds, then it has a cooldown. Only usable while moving. (Details in '⚔️ Skills'.)
                            - Let go and you glide to a stop. Your turtle always faces the way it's heading."""),
                    new Article("Getting around the screen", """
                            - **Buttons at the top right**: Turn your profile, notices, guide, minimap, leaderboard and chat on or off. A green outline means on. (On mobile: 🔍 Search · 📦 Stash · ☰ Menu.)
                            - **Right panel (on mobile the 🔍 / 📦 buttons)**: **🔍 Search** (keywords), **📦 Stash** (the count shows next to the tab), and **👥 Players** (find players by nickname).
                            - **Bottom center bar**: Your orb, level, nickname, EXP, trend score and keyword count, plus the skill buttons and emotes. Tap the portrait or name to open your profile.
- **When windows overlap**: The one you opened or tapped last is shown on top.
                            - **Mobile**: In portrait the skills sit on their own row at the bottom; in landscape the bottom bar is wide, the minimap is at the top left and the 💬 chat button at the bottom right.

                            > Shortcuts: `M` enlarge the minimap, `E` emotes, skills use the key shown on each button""")
            )),
            new Category("score-and-size", "⚔️ Score and fights", List.of(
                    new Article("Trend score and orb size", """
                            Your orb's size is set by your **trend score**: the total of the scores of the 🔥 active keywords you hold plus **your level** (1 point per level).

                            - When others also hold a keyword you hold, its score rises and your orb grows.
                            - When a keyword expires or is stolen, your orb shrinks.
                            - As your orb grows, the view zooms out a little. Speed is the same for everyone regardless of size.

                            > To see how scores are calculated, read 'How is trend score calculated?'."""),
                    new Article("What happens when orbs collide?", """
                            - Orbs bounce off each other. If the size gap is big, only the smaller one gets knocked far away.
                            - Of the two, the one with the **lower trend score** loses a keyword, and the winner **steals** it. If the scores are equal, nobody loses anything. Size doesn't decide who wins.
                            - The exception is anyone on **🔥 flame**. Against someone without a flame they win no matter the score, and the other player loses **half** of their keywords. (See "Combos and flame" below.)
                            - If the stolen keyword is already in your stash, its remaining time resets to 24 hours.
                            - You can search for a keyword you lost and add it again right away.
                            - Every collision also costs **health (HP)**. See 'Health, damage and flame math' for the numbers.
                            - **If your HP reaches 0%, or you lose while holding no keywords**, you are knocked out. (See 'Knockouts and respawning' below.)
                            - Inside a **safe zone** you don't collide with anyone.

                            ## The result cards at the top of the screen

                            When you gain or lose a keyword, a card appears at the top of the screen. A small tag on the card tells you which kind:

                            - **Stole**: You beat a player or a bot and took a keyword. The points you gained (`+`) are shown too.
                            - **Stolen**: You lost to a player or a bot and lost a keyword. The points you lost (`-`) are shown too.
                            - **Gained** / **Lost**: You gained or lost a keyword from a ? orb.
                            - Wiping out keywords with a flame, losing them all to one, and taking someone down use the same card.

                            > After losing a keyword you're invincible for 3 seconds (you'll see a 🛡 above your orb, and others pass right through you). When you join or respawn you start in the safe zone in the middle, so you're safe."""),
                    new Article("Combos and flame", """
                            Steal keywords **back to back** from collisions and you build a combo. You'll see something like **⚔️ 3 Combo** at the top of the screen.

                            - At **5 Combo**, a **🔥 flame** ignites around your orb. Everyone can see it.
                            - Keywords you win from bots, and **hitting a boss**, count toward the combo too.
                            - If you lose a keyword in a fight even once, your combo resets to 0 and the flame goes out. Beat a burning player to put out their flame!
                            - If **5 minutes** pass after your last combo gain without a new one, the combo and flame fade away. Every new combo restarts the 5 minutes. The time left shows on the combo card (under the leaderboard at the top right).
                            - If you refresh or leave and come back, your combo starts again from **0**.
                            - Bots build combos and get flames too.

                            ## 🔥 The power of flame

                            - **When a flame player hits a player without a flame**: The flame side wins regardless of trend score, and the other player loses **half** of their keywords. The flame side's combo goes up by 1. The HP damage uses **5x attack power** too.
                            - **When both have a flame**: As usual, the higher trend score wins. The loser's combo and flame disappear and they lose one keyword.
                            - Against a **bot** the same rules apply. If you have a flame and hit a bot without one, you take half of its keywords; if a flaming bot hits you and you have no flame, you lose half of your keywords.
                            - The flame side stays **in place** when you collide; only the player without a flame is knocked back.
                            - If you lose keywords, you're invincible for 3 seconds. Also, as many **red ! orbs** as **half of your lost 🔥 Active keywords** (rounded up, at least 1 if you had any active) drop around that spot. Anyone can pick them up.

                            > A flame is both a trophy and a weapon. It doesn't affect your size, but it goes out the moment you lose, so be careful."""),
                    new Article("NPC bots", """
                            Rooms with few people get red **bots** that move around with you. As people join, bots leave one by one, but at least 1 bot always stays no matter how many people there are.

                            - If a bot beats you, you lose a keyword just like when you lose to a player.
                            - If you beat a bot, you take one of the keywords that bot was holding. If you already have it, its remaining time resets to 24 hours.
                            - If you beat a bot that holds no keywords, you knock it out.
                            - Bots are named **Bot** and are red. Like players they collect keywords, eat orbs, and build combos and flames.
                            - Bots never grow bigger than the average of the players online."""),
                    new Article("Leaderboard", """
                            The leaderboard ranks the **trend score** of everyone online in this room right now. The TOP 10 appears at the top right, and you can toggle it with the 🏆 button at the top.

                            > Reaching #1, TOP 3, or TOP 10 on the leaderboard earns you titles.""")
            )),
            new Category("keywords", "🔎 Keywords", List.of(
                    new Article("Finding and adding keywords", """
                            1. Type a word into **Search** on the right.
                            2. On the result card, check its points, how many people hold it now, and its hourly activity chart.
                            3. Press **Add** and it goes into your stash and your orb grows.

                            - You can only hold one of each keyword. For a keyword you already hold, the remaining time is shown.
                            - There's no autocomplete or suggested searches. Spotting trends on your own is the fun of this game."""),
                    new Article("🔥 Active, 💤 Inactive, ⛔ Banned", """
                            [[키워드 상태]]

                            - **🔥 Active**: A keyword picked up by real search trends or held by several people. You earn points when you add it.
                            - **💤 Inactive**: A keyword that hasn't taken off yet. You can add it, but it's worth 0 points. It wakes up as 🔥 once holders gather or it shows up in trends.
                            - **⛔ Banned**: A keyword that can't be used under the operating policy."""),
                    new Article("Stash", """
                            - A keyword you add stays in your stash for **24 hours** and then disappears.
                            - By default they're listed with the most time left first. Use the sort chips at the top to sort by newest, keyword, state, or points.
                            - Keywords with less than 1 hour left are highlighted.

                            > To keep your size, you need to find new trends every day. Stealing the same keyword from someone resets its time."""),
                    new Article("You can add new words too", """
                            Slang and memes that don't show up in search can be added as they are. They start as 💤, and become 🔥 as more people add the same word.

                            If a trend you spotted before anyone else takes off, you get to grow bigger before everyone else."""),
                    new Article("Keyword cards and comments", """
                            Tap anywhere a keyword name appears (search results, your stash, `#keyword` in chat) to open its keyword card.

                            - You can leave a comment explaining why a keyword is trending or what it means.
                            - Upvoting a good comment raises the author's **comment score**. The top 3 most upvoted comments are pinned at the top with 🥇🥈🥉.""")
            )),
            new Category("arena", "🗺️ Arena", List.of(
                    new Article("Safe zones", """
                            [[아레나 표시]]

                            - **Blue square**: The safe zone that's always in the middle of the map. You start here when you join or respawn.
                            - **Green squares**: Safe zones that pop up here and there and disappear after 5 minutes. The time left is shown, and they blink for the last 30 seconds.
                            - While the center of your orb is inside a safe zone, you can't collide with anyone. You won't lose keywords either."""),
                    new Article("? orbs", """
                            Touch a **?** orb floating around the arena to eat it. The result is decided by the odds below.

                            [[orb odds]]

                            - **Gain**: One of the currently 🔥 keywords is added to your stash at random. If it is one you already hold, its remaining time is reset to 24 hours.
                            - **Lose**: You lose one of your keywords at random, active or inactive alike. If you hold none, it is a miss.
                            - The odds can change while the game runs. The bar above shows the values in use right now.

                            ## ! orbs

                            A red orb that drops around someone who lost all their keywords to a flame player.

                            - Eating one **always** gives you a 🔥 keyword. Never a miss, never a loss. (If possible you get one you don't hold yet.)
                            - It has no owner, so anyone can eat it. It disappears if left too long.

                            > Eat lots of orbs to earn titles."""),
                    new Article("Minimap and other players' info", """
                            - **Minimap**: Shows the whole map, big orbs, safe zones and your position at the bottom right (top left on mobile, in portrait and landscape). Turn it on or off with the 🗺 button at the top; click or tap the minimap, or press `M`, to enlarge it.
                            - **Other players' info**: Tap an orb once to see its name, level, trend score and keywords in a small card at the top left. Tap the name for details (trend score, level, popularity, creation date, top 3 keywords, popularity +/−, 💬 Share, 🚩 Report); `Shift`+click also works on PC. If you are not following that player, the card closes after 10 seconds, or dismiss it with ✕ or by swiping left."""),
                    new Article("Chat", """
                            You can talk with people in the same room. Turn it on or off with the 💬 button at the top, and press `Enter` to type. Writing `#keyword` makes a tappable keyword.

                            - Press **💬 Share** in a player's info and that player appears in chat as `@nickname`. Tap it to open their info.
                            - On mobile in portrait, tapping 💬 opens it at 2 lines by default; drag its top edge to change the height.

                            > Abuse or spam may get you sanctioned. Please report uncomfortable chat."""),
                    new Article("Emotes", """
                            Show how you feel with an emoji above your orb, no chat needed.

                            - Tap the **😀** button in the bottom center bar, or press `E` on PC, to open the emote window, then tap one or press `1`–`8`. The window folds after you send one.
                            - There are eight (👋 😂 😎 😭 😡 👍 ❤️ 🔥), shown briefly to everyone in the room.""")
            )),
            new Category("icons-and-titles", "🐢 Growth and customizing", List.of(
                    new Article("Levels and growth icons", """
                            Every time you add a keyword you earn experience and level up. As you level up, your turtle grows new parts one by one and gets more defined.

                            [[성장 아이콘]]

                            - **Levels 2-4**: A shell and its pattern appear.
                            - **Levels 5-6**: A head appears and opens its eyes.
                            - **Levels 7-8**: Front feet, back feet, and a tail appear.
                            - **Level 9**: It gets a smiling face.
                            - **Level 10**: The shell sparkles and it's complete."""),
                    new Article("Icon color next to your level", """
                            In the info bar at the bottom, your info, and other players' info, a small growth turtle for that level sits **next to the level number**. Its background color changes with your level. Hover over a cell to see its level.

                            [[레벨 아이콘]]

                            - **Levels 1-10**: The default green background.
                            - **From level 11**: The background color changes every 10 levels. The 10s are blue, 20s teal, 30s green, 40s lime, 50s yellow, 60s orange, 70s red, 80s pink, and 90s purple.
                            - **Within the same range**, the same color gets deeper from light to dark as the last digit goes from 0 to 9. For example, level 11 is light blue and level 19 is the deepest blue.
                            - **Level 100** has a rainbow gradient background.

                            > The picture above shows the colors currently set in the game. These colors can change during operation."""),
                    new Article("Titles", """
                            You receive titles automatically when you meet their conditions. They cover adding keywords, levels, trend score, the leaderboard, comments, searching, stealing, combos and putting out flames, bots, orbs, knockouts, getting back up after defeats, days played, and more.

                            - The moment you earn a title, a **🏅 New title alert** pops up at the top of the screen. It disappears on its own after 6 seconds, and pressing **Equip** puts that title right under your name.
                            - In **My info → 🏅 Titles** you can pick one of your titles and **equip** it. The equipped title shows at the top of the list as "Equipped", and appears in yellow under your nickname in the arena. **Unequip** it to hide it.
                            - You can also check the conditions and progress bars of titles you haven't earned yet."""),
                    new Article("Customizing your orb (from level 10)", """
                            Customize your orb in **My info → 🎨 Customize**. Your looks are saved separately in **3 profiles**, and you **equip** one of them. At first, profile 1 (the default look) is equipped.

                            - **Fill and border**: Besides the 24 suggested colors, you can pick any color you like. Dark colors too close to the floor can't be chosen.
                            - **Name tag**: Pick your nickname's text color and style (ribbon or glow).
                            - **What's inside the orb**: A 🐢 growth character or a 🎨 drawing of your own.
                            - **Border effect**: Soft glow, rotating dashes, gradient, pulse, rainbow.
                            - **Trail effect**: An effect left behind your orb as you move.

                            While you choose, the preview at the top shows your orb moving. Your changes go into that profile only when you press **Save**, and if it's the equipped profile, your orb in the arena changes right away. You can also use **Save and equip** to do both at once.

                            ## Drawing your own
                            In **✏️ Draw**, you draw on a 32×32 grid. You have a pen, eraser, fill, eyedropper, horizontal mirror, and undo (`Ctrl`+`Z`). Cells outside the dotted circle aren't shown, and empty cells show the fill color. After saving a drawing, you have to wait a bit before changing it again.

                            > Drawings and effects are only for players who are **signed in with Google**. Don't draw other people's faces or characters, or anything offensive. If you're reported, your drawing may be deleted and your saving may be suspended."""),
                    new Article("Trail effects", """
                            These are effects left behind your orb as you move. Pick one in a profile under **My info → 🎨 Customize** and save it.

                            - Choose from afterimage, stardust, flame, bubbles, rainbow, footprints (turtle, cat, dog, bird, bear, rabbit), petals, hearts, music notes, lightning, smoke, and snowflakes.
                            - When you pick one, the preview shows your orb moving with the effect. Save it to the equipped profile and everyone in the room sees it.
                            - Available from level 10, when customization unlocks, and only for players **signed in with Google**."""),
                    new Article("Bragging card", """
                            In **My info → 📸 Bragging card**, you can turn your record into a single image.

                            - It includes your orb (with your customization and trail effect), nickname, equipped title, level, current trend score, leaderboard rank (when you're in the TOP 10), a 7-day chart, 3 of your 🔥 keywords, and your totals (adds, steals, bot wins, days active).
                            - On a phone, use **Share** to send it straight to messengers or social media. On PC, use **Save image** and **Copy link**.""")
            )),
            new Category("faq", "❓ FAQ", List.of(
                    new Article("Where can I see notices?", """
                            You can see them under **📢 Notices** on the game screen. When a new notice is posted, a red dot lights up on the button (in the ☰ menu on mobile).

                            Important notices pop up once as a **📢 What's new** window when you enter the game. If there are several, flip through them with the ‹ › buttons or arrow keys, and close with **OK** or `Esc`."""),
                    new Article("Why isn't there a list of rising keywords?", """
                            If we showed everyone what's trending right now, everyone would just add the same keywords. This game is about competing on your own eye for trends, so we deliberately don't show it."""),
                    new Article("My added keyword disappeared.", """
                            Keywords you add disappear after 24 hours. You may also have lost it by colliding with another player or a bot. Either way, you can search for it and add it again."""),
                    new Article("I added a keyword but my score didn't go up.", """
                            💤 Inactive keywords are worth 0 points even when you add them. You still earn experience, and the points start coming in once the keyword becomes 🔥 Active."""),
                    new Article("My orb suddenly got smaller.", """
                            Your orb shrinks when other people hold fewer of your keywords, when a keyword expires after 24 hours, or when one is stolen in a collision. Find and add new trends and it grows again."""),
                    new Article("I keep getting my keywords stolen.", """
                            - Near big orbs, escape to a safe zone (blue or green squares). Nobody collides inside them.
                            - You're invincible for 3 seconds right after losing a keyword, so slip away during that time.
                            - The 🛡 Shield skill (from level 10) lets you pass through other circles for a moment.
                            - If you beat a bot or an orb smaller than you, you can get keywords back."""),
                    new Article("Can I continue on another device?", """
                            If you sign in with Google under **My info → Account**, you can continue with the same progress on other devices. Your guest progress carries over to that account too.""")
            )),
            new Category("account", "👤 Account and reports", List.of(
                    new Article("Where is my progress saved?", """
                            If you start without signing in, your progress stays in this browser. If you clear your browser data or switch browsers, you'll start over, so sign in with Google if you plan to play for a while."""),
                    new Article("I want to change my nickname.", """
                            You can change it in **My info**. After changing it once, you can change it again after 5 minutes.

                            You can mix English letters and numbers with one other writing system (for example, Korean). If there are characters you can't use, we'll tell you which ones."""),
                    new Article("I saw an inappropriate nickname, comment, or chat.", """
                            - **Comments**: Press 🚩 Report on the comment.
                            - **Players**: Press 🚩 Report in the other player's info window (open it by tapping an orb in the arena, or by tapping a nickname in the 👥 Players search). Pick a reason (cheating with hacks, macros or tools; bug or exploit abuse; inappropriate nickname; abuse; other) and describe what happened. You can report the same target only once a day, and you can't report yourself.
                            - **Chat**: Press report on the chat message.

                            Operators review and handle reports. Operators keep checking and banning inappropriate keywords."""),
                    new Article("What happens if I delete my account? Can I restore it?", """
                            If you signed in with Google, you can delete your account at **My info → Account → Delete account**. You must type the confirmation phrase yourself to delete it.

                            If you sign in again with the same Google account, you can choose **Restore account**. Your nickname, level, titles, customization, and comments come back as they were.""")
            ))
    );
}
