package com.trendist.seed;

import com.trendist.guide.entity.GuideCategory;
import java.util.List;

/**
 * 게임 가이드 초기 글(4.17). 지금 동작하는 규칙만 쉬운 말로 설명하고, 점수식·계수 같은 세부 수치는 쓰지 않는다.
 * 본문 문법(화면이 그리는 것만): 문단, `- ` 목록, `1. ` 번호 목록, `**굵게**`, `` `키` ``(키 모양), `> ` 팁 상자,
 * 그리고 한 문단이 `[[이름]]` 하나뿐이면 게임 그림(성장 아이콘, 시작 3단계, 조작, 키워드 상태, 아레나 표시).
 */
final class GuideSeed {
    private GuideSeed() {}

    record Article(String title, String body) {}

    record Category(String code, String name, String displayType, List<Article> articles) {}

    static final List<Category> CATEGORIES = List.of(
            new Category("getting-started", "🚀 시작하기", GuideCategory.ARTICLE, List.of(
                    new Article("Trendist는 어떤 게임인가요?", """
                            지금 뜨는 **키워드**를 남들보다 먼저 찾아 보관함에 넣으면 내 원이 커지는 실시간 아레나 게임이에요.

                            유행은 매일 바뀌어요. 사람들이 지금 무엇을 찾는지 알아채는 감각이 곧 실력이에요.

                            [[시작 3단계]]"""),
                    new Article("처음 시작하기", """
                            1. 첫 화면에서 **시작**을 누르면 로그인 없이 바로 시작해요.
                            2. 닉네임을 정하면 아레나 한가운데 파란 **세이프존**에서 출발해요.
                            3. 오른쪽 **검색**에서 요즘 화제인 단어를 찾아 **추가**해 보세요.

                            > 구글로 로그인하면 다른 기기에서도 같은 기록으로 이어서 할 수 있어요. 게스트로 쓰던 기록도 그대로 옮겨져요."""),
                    new Article("움직이기", """
                            [[조작]]

                            - **PC 마우스**: **우클릭**한 곳으로 가요(누르고 있으면 계속 따라가요). 다른 원(플레이어·봇)을 **좌클릭**하면 그 원을 자동으로 따라가요.
                            - **방향키·WASD**: 누르는 방향으로 바로 움직여요. 따라가는 중에 누르면 직접 조종으로 바뀌어요.
                            - **모바일 조이스틱**: 화면을 누른 채 끌면 그 방향으로 가요. 덜 끌면 천천히 가요. 원을 톡 누르면 그 원을 자동으로 따라가요.
                            - **⚡ 대시**: PC는 `Space`, 모바일은 아래쪽 ⚡ 버튼으로 써요. 3초 동안 평소보다 2배 빨라지고, 한 번 쓰면 쿨타임이 있어요. 움직이는 중에만 쓸 수 있어요. (자세한 내용은 '⚔️ 스킬'에서)
                            - 손을 떼면 미끄러지듯 멈춰요. 내 거북이는 늘 가는 쪽을 바라봐요."""),
                    new Article("화면 둘러보기", """
                            - **오른쪽 위 버튼**: 내 정보, 공지사항, 가이드, 미니맵, 리더보드, 채팅을 켜고 끌 수 있어요. 켜져 있으면 초록 윤곽으로 보여요. (모바일은 🔍 검색 · 📦 보관함 · ☰ 메뉴)
                            - **오른쪽 패널(모바일은 🔍·📦 버튼)**: **🔍 검색**(키워드), **📦 보관함**(개수가 탭 옆에 보여요), **👥 플레이어**(닉네임으로 플레이어 찾기)가 있어요.
                            - **아래 가운데 줄**: 내 원 모양과 레벨, 닉네임, 경험치, 유행도·키워드 수, 그리고 스킬 버튼과 이모트가 있어요. 초상화나 닉네임을 누르면 내 정보가 열려요.
- **창이 겹칠 때**: 가장 나중에 열거나 누른 것이 맨 위에 보여요.
                            - **모바일**: 세로 모드는 스킬이 아래 줄에 크게 있고, 가로 모드는 아래 줄이 넓고 미니맵이 왼쪽 위, 💬 채팅이 오른쪽 아래에 있어요.

                            > 단축키: `M` 미니맵 크게 보기, `E` 이모트, 스킬은 버튼에 적힌 키""")
            )),
            new Category("score-and-size", "⚔️ 점수와 대결", GuideCategory.ARTICLE, List.of(
                    new Article("현재 유행도와 원의 크기", """
                            내 원의 크기는 **현재 유행도**로 정해져요. 내가 가진 키워드의 점수를 모두 더한 값이에요. **레벨과 경험치는 크기에 영향이 없어요.**

                            - 내가 가진 키워드를 다른 사람도 많이 가지면 그 키워드 점수가 올라 내 원도 커져요.
                            - 키워드가 사라지거나 빼앗기면 작아져요.
                            - 원이 커지면 화면이 조금씩 넓게 보여요. 크기와 관계없이 속도는 모두 같아요."""),
                    new Article("부딪히면 어떻게 되나요?", """
                            - 원끼리 부딪히면 서로 튕겨 나가요. 크기 차이가 크면 작은 쪽만 멀리 튕겨요.
                            - 두 사람 중 **현재 유행도가 낮은 쪽**이 키워드 하나를 잃고, 이긴 쪽이 그 키워드를 **빼앗아** 와요. 점수가 같으면 아무도 잃지 않아요. 크기는 승패와 상관없어요.
                            - 단, **🔥 불꽃**이 붙은 사람은 예외예요. 불꽃 없는 사람과 부딪히면 점수와 상관없이 이기고, 상대는 키워드를 **전부** 잃어요. (아래 '연속 빼앗기와 불꽃' 참고)
                            - 빼앗은 키워드가 이미 내 보관함에 있으면, 그 키워드의 남은 시간이 다시 24시간으로 늘어나요.
                            - 잃은 키워드는 바로 다시 검색해서 추가할 수 있어요.
                            - **키워드가 하나도 없는데 지면** 죽어요. (아래 '죽음과 리스폰' 글을 보세요.)
                            - **세이프존** 안에서는 아무와도 부딪히지 않아요.

                            ## 화면 위에 뜨는 알림 카드

                            키워드를 얻거나 잃으면 화면 위에 카드가 떠요. 카드 위의 작은 표시로 구분해요.

                            - **뺏음**: 사람이나 봇을 이겨서 키워드를 가져왔어요. 얻은 점수(`+`)가 같이 보여요.
                            - **뺏김**: 사람이나 봇에게 져서 키워드를 잃었어요. 잃은 점수(`-`)가 같이 보여요.
                            - **획득**·**잃음**: ? 구슬에서 키워드를 얻거나 잃었어요.
                            - 불꽃으로 키워드를 모두 날리거나 모두 잃었을 때, 상대를 쓰러뜨렸을 때도 같은 카드로 알려 줘요.

                            > 키워드를 잃으면 3초 동안 무적이에요(원 위에 🛡가 보이고, 다른 원을 그냥 통과해요). 처음 들어오거나 리스폰할 때는 가운데 세이프존에서 시작해서 안전해요."""),
                    new Article("연속 빼앗기와 불꽃", """
                            부딪혀서 키워드를 **연속으로** 빼앗으면 콤보가 쌓여요. 화면 위에 **⚔️ 3 Combo**처럼 보여요.

                            - **5 Combo**가 되면 내 원 둘레가 **🔥 불꽃**으로 타올라요. 다른 사람에게도 그대로 보여요.
                            - 봇을 이겨 키워드를 얻은 것도 콤보에 들어가요.
                            - 한 번이라도 싸움에서 키워드를 **잃으면** 콤보가 0이 되고 불꽃도 꺼져요. 불타는 사람을 이기면 불꽃을 끌 수 있어요!
                            - 마지막으로 콤보가 오른 뒤 **5분** 동안 새 콤보가 없으면 콤보와 불꽃이 저절로 사라져요. 콤보가 오를 때마다 5분이 다시 시작돼요. 콤보 카드(오른쪽 위 리더보드 아래)에 남은 시간이 보여요.
                            - 새로고침하거나 나갔다가 다시 들어오면 콤보는 **0부터** 다시 시작해요.
                            - 봇도 콤보를 쌓고 불꽃이 붙어요.

                            ## 🔥 불꽃의 힘

                            - **불꽃인 사람이 불꽃 없는 사람과 부딪히면**: 유행도(점수)와 상관없이 불꽃 쪽이 이기고, 상대는 가진 키워드를 **전부** 잃어요. 불꽃 쪽은 콤보가 1 올라가요.
                            - **둘 다 불꽃이면**: 평소처럼 유행도가 높은 쪽이 이겨요. 진 쪽은 콤보와 불꽃이 사라지고 키워드 하나를 빼앗겨요.
                            - **봇**과 부딪힐 때도 같은 규칙이에요. 불꽃인 내가 불꽃 없는 봇과 부딪히면 봇의 키워드를 모두 날리고, 불꽃인 봇이 불꽃 없는 나와 부딪히면 내가 키워드를 모두 잃어요.
                            - 불꽃이 붙은 쪽은 부딪혀도 **제자리**에 있고, 불꽃 없는 상대만 튕겨 나가요.
                            - 키워드를 전부 잃으면 3초 동안 무적이에요. 그리고 잃은 키워드 중 **🔥활성 키워드 개수의 절반**(올림, 활성이 하나라도 있었다면 최소 1개)만큼 **빨간 ! 구슬**이 그 자리 둘레에 떨어져요. 누구나 주워 갈 수 있어요.

                            > 불꽃은 자랑거리이자 무기예요. 크기에는 영향이 없지만, 한 번 지면 꺼지니 조심하세요."""),
                    new Article("NPC 봇", """
                            사람이 적은 방에는 빨간 원 **봇**이 들어와 함께 움직여요. 사람이 들어오면 봇은 하나씩 빠지지만, 사람이 많아도 봇은 늘 1명 이상 남아 있어요.

                            - 봇에게 지면 사람에게 질 때처럼 키워드 하나를 잃어요.
                            - 봇을 이기면 그 봇이 가진 키워드 하나를 그대로 가져와요. 이미 가진 키워드면 남은 시간이 24시간으로 늘어나요.
                            - 키워드가 없는 봇에게 이기면 봇을 쓰러뜨려요.
                            - 봇은 닉네임이 **Bot**이고 원이 빨개요. 사람처럼 키워드를 모으고 구슬도 먹고 콤보·불꽃도 가져요.
                            - 봇은 접속한 사람들 평균보다 크게 자라지 않아요."""),
                    new Article("리더보드", """
                            리더보드는 지금 이 방에 접속해 있는 사람의 **현재 유행도** 순위예요. 오른쪽 위에 TOP 10이 보이고 위쪽 🏆 버튼으로 켜고 끌 수 있어요.

                            > 리더보드 1위·TOP 3·TOP 10에 오르면 칭호를 받아요.""")
            )),
            new Category("keywords", "🔎 키워드", GuideCategory.ARTICLE, List.of(
                    new Article("키워드 찾고 추가하기", """
                            1. 오른쪽 **검색**에 단어를 입력해요.
                            2. 결과 카드에서 점수, 지금 가진 사람 수, 시간별 활동 그래프를 확인해요.
                            3. **추가**를 누르면 보관함에 들어가고 원이 커져요.

                            - 같은 키워드는 하나만 가질 수 있어요. 이미 가진 키워드에는 남은 시간이 보여요.
                            - 자동완성이나 추천 검색어는 없어요. 직접 찾아내는 안목이 이 게임의 재미예요."""),
                    new Article("🔥활성, 💤비활성, ⛔금지", """
                            [[키워드 상태]]

                            - **🔥활성**: 실제 검색 트렌드에 잡혔거나 여러 사람이 가진 키워드예요. 추가하면 점수를 받아요.
                            - **💤비활성**: 아직 뜨지 않은 키워드예요. 추가는 할 수 있지만 점수는 0점이에요. 가진 사람이 모이거나 트렌드에 잡히면 🔥로 깨어나요.
                            - **⛔금지**: 운영 정책상 쓸 수 없는 키워드예요."""),
                    new Article("보관함", """
                            - 추가한 키워드는 **24시간** 동안 보관함에 있다가 사라져요.
                            - 기본으로 남은 시간이 많은 순서로 보여요. 위의 정렬 칩으로 최신순·키워드·상태·점수 순서로 바꿀 수 있어요.
                            - 남은 시간이 1시간 안이면 강조돼요.

                            > 크기를 지키려면 매일 새 유행을 찾아야 해요. 같은 키워드를 빼앗아 오면 시간이 다시 늘어나요."""),
                    new Article("새로운 단어도 추가할 수 있어요", """
                            검색해서 나오지 않는 신조어나 밈도 그대로 추가할 수 있어요. 처음에는 💤로 시작하고, 같은 단어를 추가하는 사람이 늘어나면 🔥가 돼요.

                            누구보다 먼저 찾아낸 유행이 뜨면, 그만큼 먼저 커질 수 있어요."""),
                    new Article("키워드 카드와 댓글", """
                            키워드 이름이 보이는 곳(검색 결과, 보관함, 채팅의 `#키워드`)을 누르면 키워드 카드가 열려요.

                            - 댓글로 이 키워드가 왜 뜨는지, 무슨 뜻인지 알려 줄 수 있어요.
                            - 좋은 댓글에 추천을 누르면 작성자의 **댓글 점수**가 올라요. 추천 상위 3개는 맨 위에 🥇🥈🥉로 고정돼요.""")
            )),
            new Category("arena", "🗺️ 아레나", GuideCategory.ARTICLE, List.of(
                    new Article("세이프존", """
                            [[아레나 표시]]

                            - **파란 칸**: 맵 한가운데 늘 있는 세이프존이에요. 처음 들어오거나 리스폰하면 여기서 시작해요.
                            - **연두 칸**: 곳곳에 잠깐(5분) 생겼다 사라지는 세이프존이에요. 남은 시간이 보이고, 사라지기 30초 전부터 깜빡여요.
                            - 원의 가운데가 세이프존 안에 있으면 아무와도 부딪히지 않아요. 키워드도 잃지 않아요."""),
                    new Article("? 구슬", """
                            아레나 곳곳에 떠 있는 **?** 구슬에 닿으면 먹을 수 있어요. 먹으면 아래 확률로 결과가 정해져요.

                            [[구슬 확률]]

                            - **얻음**: 지금 🔥인 키워드 중 하나가 무작위로 보관함에 들어와요. 이미 가진 키워드가 나오면 그 키워드의 남은 시간이 다시 24시간으로 갱신돼요.
                            - **잃음**: 내가 가진 키워드 하나를 무작위로 잃어요. 활성이든 비활성이든 가리지 않고, 가진 키워드가 없으면 꽝이에요.
                            - 확률은 운영 중에 바뀔 수 있어요. 위 막대는 지금 적용 중인 값이에요.

                            ## ! 구슬

                            불꽃인 사람에게 키워드를 전부 잃은 사람 둘레에 빨갛게 떨어지는 구슬이에요.

                            - 먹으면 **무조건** 🔥 키워드 하나를 얻어요. 꽝도, 잃는 일도 없어요. (가능하면 내가 안 가진 키워드가 나와요.)
                            - 주인이 없어서 누구나 먹을 수 있어요. 오래 두면 사라져요.

                            > 구슬을 많이 먹으면 칭호를 받아요."""),
                    new Article("미니맵과 다른 플레이어 정보", """
                            - **미니맵**: 오른쪽 아래(모바일은 가로·세로 모두 왼쪽 위)에 맵 전체와 큰 원들, 세이프존, 내 위치가 보여요. 위쪽 🗺 버튼으로 켜고 끄고, 미니맵을 누르거나 `M`을 누르면 크게 볼 수 있어요.
                            - **다른 플레이어 정보**: 원을 한 번 누르면 왼쪽 위에 이름·레벨·현재 유행도·가진 키워드가 작게 떠요. 이름을 누르면 자세히 볼 수 있어요(현재 유행도, 레벨, 인기도, 생성일, 가진 키워드 상위 3개, 인기도 +/−, 💬 공유, 🚩 신고). PC는 `Shift`+클릭으로도 열려요. 따라가는 중이 아니면 10초 뒤 저절로 닫히고, ✕나 왼쪽으로 밀어서 치울 수 있어요."""),
                    new Article("채팅", """
                            같은 방 사람들과 이야기할 수 있어요. 위쪽 💬 버튼으로 켜고 끄고, `Enter`를 누르면 입력할 수 있어요. `#키워드`처럼 쓰면 누를 수 있는 키워드가 돼요.

                            - 플레이어 정보의 **💬 공유**를 누르면 그 플레이어가 `@닉네임`으로 채팅에 올라가요. 누르면 그 플레이어의 정보가 열려요.
                            - 모바일 세로 모드에서는 💬를 누르면 기본 2줄로 열리고, 위쪽 끝을 끌어 높이를 바꿀 수 있어요.

                            > 욕설이나 도배는 제재될 수 있어요. 불편한 채팅은 신고해 주세요."""),
                    new Article("이모트", """
                            채팅 없이도 원 위에 이모지를 띄워 기분을 표현할 수 있어요.

                            - 아래 가운데 줄의 **😀** 버튼을 누르거나 PC에서 `E`를 눌러 이모트 창을 열고, 누르거나 숫자 `1`~`8`로 골라요. 하나를 보내면 창이 접혀요.
                            - 👋 😂 😎 😭 😡 👍 ❤️ 🔥 여덟 가지가 있고, 같은 방 모두에게 잠깐 보여요.""")
            )),
            new Category("icons-and-titles", "🐢 성장과 꾸미기", GuideCategory.ARTICLE, List.of(
                    new Article("레벨과 성장 아이콘", """
                            키워드를 추가하거나, 싸움에서 이기거나, 상대를 쓰러뜨릴 때마다 경험치가 쌓이고 레벨이 올라요. 레벨이 오를수록 내 거북이에 부위가 하나씩 생기고 또렷하게 자라요.

                            [[성장 아이콘]]

                            - **2~4레벨**: 등껍질과 무늬가 생겨요.
                            - **5~6레벨**: 머리가 나오고 눈을 떠요.
                            - **7~8레벨**: 앞발, 뒷발, 꼬리가 생겨요.
                            - **9레벨**: 웃는 얼굴이 돼요.
                            - **10레벨**: 등껍질이 반짝이며 완성돼요."""),
                    new Article("레벨 옆 아이콘 색", """
                            아래 정보 줄, 내 정보, 다른 사람 정보에서 **레벨 숫자 옆**에는 그 레벨의 성장 거북이가 작게 붙어요. 바탕색은 레벨에 따라 이렇게 바뀌어요. 칸에 마우스를 올리면 레벨이 보여요.

                            [[레벨 아이콘]]

                            - **1~10레벨**: 기본 초록 바탕이에요.
                            - **11레벨부터**: 10레벨 구간마다 바탕색이 바뀌어요. 10대는 파랑, 20대는 청록, 30대는 초록, 40대는 연두, 50대는 노랑, 60대는 주황, 70대는 빨강, 80대는 분홍, 90대는 보라예요.
                            - **같은 구간 안**에서는 일의 자리가 0에서 9로 갈수록 같은 색이 옅은 색에서 진한 색으로 짙어져요. 예를 들어 11레벨은 옅은 파랑, 19레벨은 가장 진한 파랑이에요.
                            - **100레벨**은 무지개 그라데이션 바탕이에요.

                            > 위 그림은 지금 게임에 설정된 색을 그대로 보여 줘요. 이 색은 운영 중에 바뀔 수 있어요."""),
                    new Article("칭호", """
                            조건을 채우면 칭호를 자동으로 받아요. 키워드 추가, 레벨, 유행도, 리더보드, 댓글, 검색, 빼앗기, 콤보와 불꽃 끄기, 봇, 구슬, 상대를 쓰러뜨린 횟수, 졌다가 다시 일어선 횟수, 꾸준히 플레이한 날 등 다양한 칭호가 있어요.

                            - 칭호를 받는 순간 화면 위에 **🏅 새 칭호 알림**이 떠요. 6초 뒤 저절로 사라지고, **장착하기**를 누르면 그 칭호가 바로 내 이름 아래에 달려요.
                            - **내 정보 → 🏅 칭호**에서 받은 칭호를 하나 골라 **장착**할 수 있어요. 장착한 칭호는 목록 맨 위에 '장착 중'으로 보이고, 아레나에서 닉네임 아래에 노란 글씨로 보여요. **해제**하면 숨겨져요.
                            - 아직 못 받은 칭호도 조건과 진행 막대로 확인할 수 있어요."""),
                    new Article("원 꾸미기 (10레벨부터)", """
                            **내 정보 → 🎨 꾸미기**에서 내 원을 꾸며요. 꾸민 모습은 **프로필 3개**에 따로 저장해 두고, 그중 하나를 **장착**해서 써요. 처음에는 1번 프로필(기본 모습)이 장착되어 있어요.

                            - **바탕색·테두리**: 24가지 추천 색 말고도 원하는 색을 직접 고를 수 있어요. 바닥과 비슷한 어두운 색은 고를 수 없어요.
                            - **이름표**: 닉네임 글자색과 모양(리본·빛)을 골라요.
                            - **원 안에 그릴 것**: 🐢 성장 캐릭터 또는 🎨 직접 그린 그림.
                            - **테두리 효과**: 은은한 빛, 도는 점선, 그라데이션, 맥박, 무지개.
                            - **이동 효과**: 움직일 때 원 뒤에 남는 효과.

                            고르는 동안은 위의 미리 보기에서 원이 움직이며 보여 줘요. **저장**을 눌러야 그 프로필에 들어가고, 장착한 프로필이면 아레나의 내 원도 바로 바뀌어요. **저장하고 장착**으로 한 번에 바꿀 수도 있어요.

                            ## 직접 그리기
                            **✏️ 그림 그리기**에서 32×32 칸에 그림을 그려요. 펜, 지우개, 채우기, 스포이드, 좌우 대칭, 되돌리기(`Ctrl`+`Z`)를 쓸 수 있어요. 점선 원 밖의 칸은 보이지 않고, 비워 둔 칸에는 바탕색이 보여요. 그림은 잠깐 기다렸다가 다시 바꿀 수 있어요.

                            > 그림과 효과는 **Google로 로그인**한 사람만 쓸 수 있어요. 남의 얼굴이나 캐릭터, 불쾌한 그림은 그리지 마세요. 신고되면 그림이 지워지고 저장이 정지될 수 있어요."""),
                    new Article("이동 효과", """
                            움직일 때 내 원 뒤에 남는 효과예요. **내 정보 → 🎨 꾸미기**의 프로필에서 골라 저장해요.

                            - 잔상, 별가루, 불꽃, 물방울, 무지개, 발자국(거북이·고양이·강아지·새·곰·토끼), 꽃잎, 하트, 음표, 번개, 연기, 눈송이가 있어요.
                            - 고르면 미리 보기에서 원이 움직이며 효과를 보여 줘요. 장착한 프로필에 저장하면 같은 방의 모두에게 보여요.
                            - 꾸미기가 열리는 10레벨부터, **Google로 로그인한 사람**만 쓸 수 있어요."""),
                    new Article("자랑하기 카드", """
                            **내 정보 → 📸 자랑하기 카드**에서 내 기록을 이미지 한 장으로 만들 수 있어요.

                            - 내 원(꾸미기·이동 효과 그대로), 닉네임, 장착한 칭호, 레벨, 현재 유행도, 리더보드 순위(TOP 10 안일 때), 최근 7일 그래프, 가진 🔥 키워드 3개, 누적 기록(추가·빼앗기·봇 사냥·활동한 날)이 들어가요.
                            - 휴대폰은 **공유하기**로 메신저·SNS에 바로 보내고, PC는 **이미지 저장**과 **링크 복사**를 써요.""")
            )),
            new Category("faq", "❓ 자주 묻는 질문", GuideCategory.FAQ, List.of(
                    new Article("공지는 어디서 보나요?", """
                            게임 화면의 **📢 공지사항**에서 볼 수 있어요. 새 공지가 올라오면 버튼에 빨간 점이 켜져요(모바일은 ☰ 메뉴).

                            중요한 공지는 게임에 들어올 때 **📢 새 소식** 창으로 한 번 떠요. 여러 개면 ‹ › 버튼이나 방향키로 넘겨 보고, **확인**이나 `Esc`로 닫아요."""),
                    new Article("급상승 키워드 목록은 왜 없나요?", """
                            지금 뜨는 키워드를 모두에게 보여 주면 모두가 같은 키워드만 따라 추가하게 돼요. 각자 유행을 찾아내는 안목을 겨루는 게임이라 일부러 보여 주지 않아요."""),
                    new Article("추가한 키워드가 사라졌어요.", """
                            추가한 키워드는 24시간이 지나면 사라져요. 다른 플레이어나 봇과 부딪혀서 잃었을 수도 있어요. 어느 쪽이든 다시 검색해서 추가할 수 있어요."""),
                    new Article("추가했는데 점수가 오르지 않아요.", """
                            💤비활성 키워드는 추가해도 점수가 0점이에요. 경험치는 쌓이고, 키워드가 🔥활성이 되면 그때부터 점수가 들어와요."""),
                    new Article("원이 갑자기 작아졌어요.", """
                            가진 키워드를 다른 사람이 덜 갖게 됐거나, 24시간이 지나 사라졌거나, 부딪혀서 빼앗겼을 때 작아져요. 새 유행을 찾아 추가하면 다시 커져요."""),
                    new Article("자꾸 키워드를 빼앗겨요.", """
                            - 큰 원 근처에서는 세이프존(파란 칸·연두 칸)으로 피하세요. 안에서는 부딪히지 않아요.
                            - 키워드를 잃은 직후 3초는 무적이니 그 사이에 빠져나가세요.
                            - 🛡 방패 스킬(10레벨부터)을 쓰면 잠깐 다른 원을 통과할 수 있어요.
                            - 봇이나 나보다 작은 원을 이기면 키워드를 다시 얻을 수 있어요."""),
                    new Article("다른 기기에서 이어서 할 수 있나요?", """
                            **내 정보 → 계정**에서 구글로 로그인하면 다른 기기에서도 같은 기록으로 이어서 할 수 있어요. 게스트로 쓰던 기록도 그 계정으로 옮겨져요.""")
            )),
            new Category("account", "👤 계정과 신고", GuideCategory.FAQ, List.of(
                    new Article("기록은 어디에 남나요?", """
                            로그인 없이 시작하면 이 브라우저에 기록이 이어져요. 브라우저 데이터를 지우거나 다른 브라우저로 오면 새로 시작하게 되니, 오래 할 거라면 구글로 로그인해 두세요."""),
                    new Article("닉네임을 바꾸고 싶어요.", """
                            **내 정보**에서 바꿀 수 있어요. 한 번 바꾸면 5분 뒤에 다시 바꿀 수 있어요.

                            영문·숫자와 한 가지 문자(예: 한글)를 섞어 쓸 수 있어요. 쓸 수 없는 글자가 있으면 어떤 글자인지 알려 드려요."""),
                    new Article("부적절한 닉네임, 댓글, 채팅을 봤어요.", """
                            - **댓글**: 댓글의 🚩 신고를 눌러 주세요.
                            - **플레이어**: 다른 플레이어 정보 창(아레나에서 원을 눌러 열거나, 👥 플레이어 검색에서 닉네임을 눌러 열어요)의 🚩 신고를 눌러 주세요. 사유는 부정행위(핵·매크로·프로그램), 버그·허점 악용, 부적절한 닉네임, 욕설·비방, 기타 중에서 고르고 상황을 적을 수 있어요. 같은 대상은 하루에 한 번만 신고할 수 있고, 나 자신은 신고할 수 없어요.
                            - **채팅**: 채팅 메시지의 신고를 눌러 주세요.

                            운영자가 확인한 뒤 처리해요. 부적절한 키워드는 운영자가 계속 확인하고 금지하고 있어요."""),
                    new Article("탈퇴하면 어떻게 되나요? 복구할 수 있나요?", """
                            구글로 로그인한 계정은 **내 정보 → 계정 → 회원 탈퇴**에서 탈퇴할 수 있어요. 확인 문구를 직접 입력해야 탈퇴돼요.

                            같은 구글 계정으로 다시 로그인하면 **계정 복구**를 고를 수 있어요. 닉네임, 레벨, 칭호, 꾸미기, 댓글이 그대로 돌아와요.""")
            ))
    );

    /**
     * 이미 만든 DB의 가이드 글 고치기(4.17): 예전 설명이 지금 게임과 달라졌을 때, **DB의 본문이 처음 시드 그대로일 때만** 새 본문(CATEGORIES·EnglishSeed의 같은 제목 글)으로 바꾼다.
     * 관리자가 이미 고친 글은 건드리지 않는다. 영어 본문도 함께 바꾼다.
     */
    record Fix(String categoryCode, String title, String oldBody) {}

    static final List<Fix> FIXES = List.of(
            new Fix("getting-started", "움직이기", """
                            [[조작]]

                            - **클릭·탭**: 빈 바닥을 누르면 그곳으로 가요. 누른 채 끌면 계속 따라가요.
                            - **방향키·WASD**: 누르는 방향으로 바로 움직여요.
                            - **모바일 조이스틱**: 화면을 누른 채 끌면 그 방향으로 가요. 덜 끌면 천천히 가요.
                            - **⚡ 대시**: PC는 `Space`, 모바일은 조이스틱을 쥔 채 **다른 손가락으로 화면 아무 곳이나 탭**하면 돼요. 3초 동안 평소보다 2배 빨라지고, 한 번 쓰면 30초 뒤에 다시 쓸 수 있어요. 움직이는 중에만 써져요. 오른쪽 아래 이모트 버튼 왼쪽의 ⚡에서 남은 시간을 볼 수 있어요.
                            - 손을 떼면 미끄러지듯 멈춰요. 내 거북이는 늘 가는 쪽을 바라봐요."""),
            new Fix("getting-started", "화면 둘러보기", """
                            - **오른쪽 위 버튼**: 내 정보, 공지사항, 가이드, 미니맵, 리더보드, 채팅을 켜고 끌 수 있어요.
                            - **오른쪽(모바일은 아래) 패널**: 키워드 **검색**과 내 **보관함**이 있어요.
                            - **아래쪽**: 내 닉네임, 레벨, 현재 유행도가 보여요. 닉네임을 누르면 내 정보가 열려요.

                            > 단축키: `M` 미니맵, `L` 리더보드, `C` 채팅"""),
            new Fix("score-and-size", "현재 유행도와 원의 크기", """
                            내 원의 크기는 **현재 유행도**로 정해져요. 내가 가진 키워드의 점수에, 추가를 많이 할수록 쌓이는 경험치가 더해져요.

                            - 내가 가진 키워드를 다른 사람도 많이 가지면 그 키워드 점수가 올라 내 원도 커져요.
                            - 키워드가 사라지거나 빼앗기면 작아져요.
                            - 원이 커지면 화면이 조금씩 넓게 보여요. 크기와 관계없이 속도는 모두 같아요."""),
            new Fix("arena", "? 구슬", """
                            아레나 곳곳에 떠 있는 **?** 구슬에 닿으면 먹을 수 있어요.

                            - 운이 좋으면 지금 🔥인 키워드 하나가 보관함에 들어와요.
                            - 꽝일 때도 있어요. 이미 가진 키워드가 나오면 그 키워드의 만료시간이 다시 24시간으로 갱신돼요.
                            - 운이 나쁘면 내가 가진 키워드 하나를 잃기도 해요. 활성이든 비활성이든 가리지 않아요. 얻을 확률과 잃을 확률은 반반이에요.

                            ## ! 구슬

                            불꽃인 사람에게 키워드를 전부 잃은 사람 둘레에 빨갛게 떨어지는 구슬이에요.

                            - 먹으면 **무조건** 🔥 키워드 하나를 얻어요. 꽝도, 잃는 일도 없어요. (가능하면 내가 안 가진 키워드가 나와요.)
                            - 주인이 없어서 누구나 먹을 수 있어요. 오래 두면 사라져요.

                            > 구슬을 많이 먹으면 칭호를 받아요."""),
            new Fix("arena", "미니맵과 다른 플레이어 정보", """
                            - **미니맵**(`M`): 오른쪽 아래에 맵 전체와 큰 원들, 세이프존, 내 위치가 보여요.
                            - **다른 플레이어 정보**: PC는 다른 원을 **우클릭**, 모바일은 🔍을 누른 뒤 원을 탭하면 레벨, 칭호, 점수 추이, 가진 키워드 상위 3개를 볼 수 있어요."""),
            new Fix("arena", "채팅", """
                            같은 방 사람들과 이야기할 수 있어요(`C`로 켜고 끄기). `#키워드`처럼 쓰면 누를 수 있는 키워드가 돼요.

                            > 욕설이나 도배는 제재될 수 있어요. 불편한 채팅은 신고해 주세요."""),
            new Fix("arena", "이모트", """
                            채팅 없이도 원 위에 이모지를 띄워 기분을 표현할 수 있어요.

                            - 화면 오른쪽 아래 **😀** 버튼을 누르거나 PC에서 `E`를 눌러 이모트 창을 열고, 누르거나 숫자 `1`~`8`로 골라요. 하나를 보내면 창이 접혀요.
                            - 👋 😂 😎 😭 😡 👍 ❤️ 🔥 여덟 가지가 있고, 같은 방 모두에게 잠깐 보여요.""")
            ,new Fix("getting-started", "화면 둘러보기", """
                            - **오른쪽 위 버튼**: 내 정보, 공지사항, 가이드, 미니맵, 리더보드, 채팅을 켜고 끌 수 있어요. 켜져 있으면 초록 윤곽으로 보여요. (모바일은 🔍 검색 · 📦 보관함 · ☰ 메뉴)
                            - **오른쪽 패널(모바일은 🔍·📦 버튼)**: 키워드 **검색**과 내 **보관함**(개수가 탭 옆에 보여요)이 있어요.
                            - **아래 가운데 줄**: 내 원 모양과 레벨, 닉네임, 경험치, 유행도·키워드 수, 그리고 스킬 버튼과 이모트가 있어요. 초상화나 닉네임을 누르면 내 정보가 열려요.

                            > 단축키: `M` 미니맵 크게 보기, `E` 이모트, 스킬은 버튼에 적힌 키""")
            ,new Fix("score-and-size", "부딪히면 어떻게 되나요?", """
                            - 원끼리 부딪히면 서로 튕겨 나가요. 크기 차이가 크면 작은 쪽만 멀리 튕겨요.
                            - 두 사람 중 **현재 유행도가 낮은 쪽**이 키워드 하나를 잃고, 이긴 쪽이 그 키워드를 **빼앗아** 와요.
                            - 단, **🔥 불꽃**이 붙은 사람은 예외예요. 불꽃 없는 사람과 부딪히면 점수와 상관없이 이기고, 상대는 키워드를 **전부** 잃어요. (아래 '연속 빼앗기와 불꽃' 참고)
                            - 빼앗은 키워드가 이미 내 보관함에 있으면, 그 키워드의 남은 시간이 다시 24시간으로 늘어나요.
                            - 잃은 키워드는 바로 다시 검색해서 추가할 수 있어요.

                            > 키워드를 잃으면 3초 동안 무적이에요(원 위에 🛡가 보여요). 막 들어온 사람도 잠깐 보호돼요.""")
            ,new Fix("score-and-size", "연속 빼앗기와 불꽃", """
                            부딪혀서 키워드를 **연속으로** 빼앗으면 콤보가 쌓여요. 화면 위에 **⚔️ 3 Combo**처럼 보여요.

                            - **5 Combo**가 되면 내 원 둘레가 **🔥 불꽃**으로 타올라요. 다른 사람에게도 그대로 보여요.
                            - 봇을 이겨 키워드를 얻은 것도 콤보에 들어가요.
                            - 한 번이라도 부딪혀 키워드를 **빼앗기면** 콤보가 0이 되고 불꽃도 꺼져요. 불타는 사람을 이기면 불꽃을 끌 수 있어요!

                            ## 🔥 불꽃의 힘

                            - **불꽃인 사람이 불꽃 없는 사람과 부딪히면**: 유행도(점수)와 상관없이 불꽃 쪽이 이기고, 상대는 가진 키워드를 **전부** 잃어요. 불꽃 쪽은 콤보가 1 올라가요.
                            - **둘 다 불꽃이면**: 평소처럼 유행도가 높은 쪽이 이겨요. 진 쪽은 콤보와 불꽃이 사라지고 키워드 하나를 빼앗겨요.
                            - **봇**과 부딪힐 때는 불꽃이어도 평소 규칙 그대로예요.
                            - 키워드를 전부 잃으면 3초 동안 무적이에요. 그리고 잃은 키워드 중 **🔥활성 키워드 개수의 절반**(올림, 활성이 하나라도 있었다면 최소 1개)만큼 **빨간 ! 구슬**이 그 자리 둘레에 떨어져요. 누구나 주워 갈 수 있어요.

                            > 불꽃은 자랑거리이자 무기예요. 크기에는 영향이 없지만, 한 번 지면 꺼지니 조심하세요.""")
            ,new Fix("score-and-size", "NPC 봇", """
                            사람이 적은 방에는 빨간 원 **봇**이 들어와 함께 움직여요. 사람이 들어오면 봇은 하나씩 빠지지만, 사람이 많아도 봇은 늘 1명 이상 남아 있어요.

                            - 봇에게 지면 사람에게 질 때처럼 키워드 하나를 잃어요.
                            - 봇을 이기면 지금 🔥인 키워드 하나를 무작위로 얻어요. 이미 가진 키워드면 남은 시간이 24시간으로 늘어나요.
                            - 봇은 접속한 사람들 평균보다 크게 자라지 않아요.""")
            ,new Fix("score-and-size", "리더보드", """
                            리더보드는 지금 이 방에 접속해 있는 사람의 **현재 유행도** 순위예요. 오른쪽 위에 TOP 10이 보이고 `L` 키로 켜고 끌 수 있어요.

                            > 리더보드 1위·TOP 3·TOP 10에 오르면 칭호를 받아요.""")
            ,new Fix("arena", "세이프존", """
                            [[아레나 표시]]

                            - **파란 칸**: 맵 한가운데 늘 있는 세이프존이에요.
                            - **연두 칸**: 곳곳에 잠깐 생겼다 사라지는 세이프존이에요. 남은 시간이 보이고, 사라지기 30초 전부터 깜빡여요.
                            - 원의 가운데가 세이프존 안에 있으면 아무와도 부딪히지 않아요. 키워드도 잃지 않아요.""")
            ,new Fix("arena", "미니맵과 다른 플레이어 정보", """
                            - **미니맵**: 오른쪽 아래(모바일 세로는 왼쪽 위)에 맵 전체와 큰 원들, 세이프존, 내 위치가 보여요. 위쪽 🗺 버튼으로 켜고 끄고, 미니맵을 누르거나 `M`을 누르면 크게 볼 수 있어요.
                            - **다른 플레이어 정보**: 원을 한 번 누르면 왼쪽 위에 이름·레벨·현재 유행도·가진 키워드가 작게 떠요. 이름을 누르면 자세히(신고 포함), PC는 `Shift`+클릭으로도 열려요. 따라가는 중이 아니면 10초 뒤 저절로 닫히고, ✕나 왼쪽으로 밀어서 치울 수 있어요.""")
            ,new Fix("arena", "채팅", """
                            같은 방 사람들과 이야기할 수 있어요. 위쪽 💬 버튼으로 켜고 끄고, `Enter`를 누르면 입력할 수 있어요. `#키워드`처럼 쓰면 누를 수 있는 키워드가 돼요.

                            > 욕설이나 도배는 제재될 수 있어요. 불편한 채팅은 신고해 주세요.""")
            ,new Fix("icons-and-titles", "레벨과 성장 아이콘", """
                            추가할 때마다 경험치가 쌓이고 레벨이 올라요. 레벨이 오를수록 내 거북이에 부위가 하나씩 생기고 또렷하게 자라요.

                            [[성장 아이콘]]

                            - **2~4레벨**: 등껍질과 무늬가 생겨요.
                            - **5~6레벨**: 머리가 나오고 눈을 떠요.
                            - **7~8레벨**: 앞발, 뒷발, 꼬리가 생겨요.
                            - **9레벨**: 웃는 얼굴이 돼요.
                            - **10레벨**: 등껍질이 반짝이며 완성돼요.""")
            ,new Fix("icons-and-titles", "칭호", """
                            조건을 채우면 칭호를 자동으로 받아요. 키워드 추가, 레벨, 유행도, 리더보드, 댓글, 검색, 빼앗기, 봇, 구슬, 상대를 쓰러뜨린 횟수, 꾸준히 플레이한 날 등 다양한 칭호가 있어요.

                            - 칭호를 받는 순간 화면 위에 **🏅 새 칭호 알림**이 떠요. 6초 뒤 저절로 사라지고, **장착하기**를 누르면 그 칭호가 바로 내 이름 아래에 달려요.
                            - **내 정보 → 🏅 칭호**에서 받은 칭호를 하나 골라 **장착**할 수 있어요. 장착한 칭호는 목록 맨 위에 '장착 중'으로 보이고, 아레나에서 닉네임 아래에 노란 글씨로 보여요. **해제**하면 숨겨져요.
                            - 아직 못 받은 칭호도 조건과 진행 막대로 확인할 수 있어요.""")
            ,new Fix("faq", "자꾸 키워드를 빼앗겨요.", """
                            - 큰 원 근처에서는 세이프존(파란 칸·연두 칸)으로 피하세요. 안에서는 부딪히지 않아요.
                            - 키워드를 잃은 직후 3초는 무적이니 그 사이에 빠져나가세요.
                            - 봇이나 나보다 작은 원을 이기면 키워드를 다시 얻을 수 있어요.""")
            ,new Fix("account", "부적절한 닉네임, 댓글, 채팅을 봤어요.", """
                            - **댓글**: 댓글의 🚩 신고를 눌러 주세요.
                            - **플레이어**: 다른 플레이어 정보 창의 🚩 신고를 눌러 주세요.
                            - **채팅**: 채팅 메시지의 신고를 눌러 주세요.

                            운영자가 확인한 뒤 처리해요. 부적절한 키워드는 운영자가 계속 확인하고 금지하고 있어요.""")
            ,new Fix("account", "부적절한 닉네임, 댓글, 채팅을 봤어요.", """
                            - **댓글**: 댓글의 🚩 신고를 눌러 주세요.
                            - **플레이어**: 다른 플레이어 정보 창(아레나에서 원을 눌러 열거나, 👥 플레이어 검색에서 닉네임을 눌러 열어요)의 🚩 신고를 눌러 주세요. 사유는 부정행위(핵·매크로·프로그램), 버그·허점 악용, 부적절한 닉네임, 욕설·비방, 기타 중에서 고르고 상황을 적을 수 있어요. 같은 플레이어는 하루에 한 번 신고할 수 있고, 나 자신은 신고할 수 없어요.
                            - **채팅**: 채팅 메시지의 신고를 눌러 주세요.

                            운영자가 확인한 뒤 처리해요. 부적절한 키워드는 운영자가 계속 확인하고 금지하고 있어요.""")
    );

    /**
     * 나중에 더한 가이드 카테고리와 글(4.17): 가이드는 운영에서 고치는 데이터라 처음에만 넣지만, 새 기능 글은 이미 만든 DB에도 한 번 넣어야 한다.
     * 카테고리는 같은 코드가 없을 때만 afterCode 카테고리 바로 뒤에 끼워 넣고(뒤 카테고리 순서를 하나씩 밀어서), 글은 같은 카테고리에 같은 제목이 없을 때만 맨 뒤에 넣는다.
     * 관리자가 고치거나 지운 것을 되살리지는 않는다. 영어도 함께 넣는다. 글 본문의 `[[스킬 …]]`·`[[스킬 해금 표]]`·`[[구슬 확률]]`은 게임 그림(가이드 그림)이다.
     */
    record AddedCategory(String code, String name, String nameEn, String displayType, String afterCode) {}

    record Added(String categoryCode, String title, String body, String titleEn, String bodyEn) {}

    static final List<AddedCategory> ADDED_CATEGORIES = List.of(
            new AddedCategory("skills", "⚔️ 스킬", "⚔️ Skills", GuideCategory.ARTICLE, "icons-and-titles"));

    static final List<Added> ADDED = List.of(
            new Added("icons-and-titles", "레벨별 필요 경험치", """
                    레벨은 **경험치(EXP)**가 쌓이면 올라요. 아래 표는 **모든 레벨**마다 **앞 레벨에서 올라올 때 필요한 경험치**예요.

                    [[레벨 필요 경험치]]

                    - 경험치는 키워드를 **추가**할 때마다, 공격에 **성공**할 때마다, 상대를 **쓰러뜨릴** 때마다 쌓여요(기본값: 추가 1, 공격 성공 1, 처치 5).
                    - 1~10레벨은 빠르게 올라가고, 11레벨부터는 레벨이 높을수록 점점 더 많이 필요해요.
                    - 표의 값은 **지금 적용 중인 값**이에요. 운영 중에 바뀔 수 있어요.
                    - 레벨이 오르면 필요한 경험치만큼 쓰고 **남은 경험치는 다음 레벨로 이어져요.** 레벨은 나라마다 따로 쌓여요.

                    > 레벨은 키워드 점수나 원의 크기에는 영향이 없어요. 거북이 아이콘이 자라고, 꾸미기와 스킬이 열리는 열쇠예요.""",
                    "EXP needed per level", """
                    Your level goes up as you collect **experience (EXP)**. The table below shows the **EXP needed to reach each level from the one before it**.

                    [[level exp table]]

                    - You earn EXP each time you **add** a keyword, each time an attack **succeeds**, and each time you **take someone down** (defaults: add 1, successful attack 1, takedown 5).
                    - Levels 1–10 come quickly; from level 11 on, higher levels need more and more.
                    - The values in the table are the ones **in use right now**. They can change while the game runs.
                    - When you level up, the EXP it needed is spent and **any leftover EXP carries over** to the next level. Levels are tracked separately for each country.

                    > Your level does not affect keyword score or circle size. It makes your turtle icon grow and unlocks customizing and skills."""),
            new Added("skills", "스킬 한눈에 보기", """
                    레벨이 오르면 쓸 수 있는 **스킬**이 늘어나요. **⚡ 대시**는 처음부터 쓸 수 있는 기본 스킬이고, 나머지는 아래 레벨이 되어야 해요. 화면 아래 가운데 줄의 버튼을 누르거나 키보드 키로 써요.

                    [[스킬 해금 표]]

                    - **필요 EXP**는 그 레벨이 되는 데 모아야 하는 **누적 경험치**예요. 경험치는 키워드를 **추가**할 때마다, 공격에 **성공**할 때마다, 상대를 **쓰러뜨릴** 때마다 쌓여요(기본값: 추가 1, 공격 성공 1, 처치 5).
                    - 위 표의 값(레벨, 필요 EXP, 키, 쿨타임)은 **지금 적용 중인 값**이에요. 운영 중에 바뀔 수 있어요.
                    - 아직 레벨이 모자란 스킬은 버튼에 **🔒와 필요한 레벨**이 보여요. 레벨이 되면 "스킬을 쓸 수 있어요!" 안내가 떠요.
                    - 스킬마다 **쿨타임**이 있어요. 쓰면 버튼에 남은 초가 보이고, 새로고침해도 쿨타임은 이어져요.
                    - PC는 버튼에 마우스를 올리면 설명과 쿨타임, 지금 쓸 수 있는지가 카드로 보여요.
                    - 갈고리와 충격파는 **세이프존 안에서는 쓸 수 없어요.**

                    > 레벨은 키워드 점수나 원의 크기에는 영향이 없어요. 스킬을 쓸 수 있게 하는 열쇠예요. 각 스킬이 내 화면과 상대 화면에서 어떻게 보이는지는 아래 글마다 움직이는 그림으로 볼 수 있어요.""",
                    "Skills at a glance", """
                    As your level rises, you can use more **skills**. **⚡ Dash** is the default skill you have from the start; the others need the levels below. Use the buttons in the bottom center bar, or press the keys.

                    [[skill unlock table]]

                    - **EXP needed** is the **total experience** you must collect to reach that level. You earn EXP each time you **add** a keyword, each time an attack **succeeds**, and each time you **take someone down** (defaults: add 1, successful attack 1, takedown 5).
                    - The values in the table (level, EXP needed, key, cooldown) are the ones **in use right now**. They can change while the game runs.
                    - Skills you haven't unlocked yet show a **🔒 and the level you need** on the button. When you reach it, a "You can now use a skill!" notice appears.
                    - Each skill has a **cooldown**. After using one, the button shows the seconds left, and it carries over even if you refresh.
                    - On PC, hover a button to see its description, cooldown and whether it is ready.
                    - Hook and Shockwave **can't be used inside safe zones.**

                    > Your level does not affect keyword score or circle size. It is the key that unlocks skills. Each article below shows, with a moving picture, how the skill looks on your screen and on the other player's screen."""),
            new Added("skills", "⚡ 대시", """
                    움직이는 방향으로 **잠깐 평소의 2배 속도**로 달려요. 처음부터 쓸 수 있는 기본 스킬이고, 움직이는 중에만 쓸 수 있어요.

                    [[스킬 대시]]

                    - **언제 쓸까요**: 큰 원에게 쫓길 때 도망치거나, 도망가는 상대를 따라잡을 때, 구슬을 먼저 먹으러 갈 때 좋아요.
                    - **내 화면**: 하늘색 빛과 꼬리가 남고 평소보다 빠르게 나아가요.
                    - **상대 화면**: 상대가 순식간에 멀어져요. 쿨타임이 있으니 대시가 끝난 뒤에 따라잡을 수 있어요.
                    - 쿨타임 동안에는 다시 쓸 수 없어요. 남은 시간은 버튼에서 볼 수 있어요.""",
                    "⚡ Dash", """
                    Dash in the direction you are moving at **twice your normal speed for a moment**. It is the default skill, and only usable while moving.

                    [[skill dash]]

                    - **When to use it**: Escape a bigger circle chasing you, catch up to someone running away, or beat others to an orb.
                    - **Your screen**: A light-blue glow and trail follow you as you speed ahead.
                    - **The other player's screen**: You get away in an instant. Since there is a cooldown, you can catch up after the dash ends.
                    - You can't dash again during the cooldown. The seconds left show on the button."""),
            new Added("skills", "🛡 방패", """
                    **잠깐 다른 원과 부딪히지 않고 통과해요.** 해금 레벨은 위 표를 보세요. 키워드를 잃은 뒤의 무적과 같은 상태예요.

                    [[스킬 방패]]

                    - **언제 쓸까요**: 점수가 높은 큰 원이 바로 앞까지 다가왔을 때 빠져나가기, 키워드를 뺏기지 않고 지나가야 할 때 좋아요.
                    - **내 화면**: 내 원에 점선 보호막과 남은 초가 떠요. 다가오는 원이 내 원을 그냥 통과해요.
                    - **상대 화면**: 방패를 쓴 원도 보호막이 보여요. 그 원에게는 밀리지도, 키워드를 잃지도 않지만 나도 그 원을 공격할 수 없어요.
                    - 보호막이 사라지면 다시 평소처럼 부딪혀요.""",
                    "🛡 Shield", """
                    **Briefly pass through other circles without colliding.** See the table above for the unlock level. It works like the invulnerability you get after losing a keyword.

                    [[skill shield]]

                    - **When to use it**: Slip away when a high-scoring big circle gets right in front of you, or when you must pass by without losing a keyword.
                    - **Your screen**: A dashed shield ring and the seconds left appear on your circle. Circles coming at you simply pass through.
                    - **The other player's screen**: A shielded circle also shows its ring. You are not pushed or robbed by it, but you can't attack it either.
                    - When the shield ends, collisions work as usual again."""),
            new Added("skills", "🪝 갈고리", """
                    겨눈 방향으로 **갈고리를 던져서** 맞은 원의 **키워드 1개를 빼앗고 나에게 끌어와요.** 해금 레벨은 위 표를 보세요. **세이프존 안에서는 쓸 수 없어요.**

                    [[스킬 갈고리]]

                    - **겨누는 법**: PC는 마우스가 있는 쪽, 모바일은 조이스틱을 누른 방향(없으면 가던 방향)으로 나가요. 사거리는 내 원 반지름의 5배쯤이고, 범위는 점선으로 먼저 보여요.
                    - **내 화면**: 갈고리 줄이 뻗어 나가 맞은 원을 붙잡고 끌어당겨요. 끌려온 원과는 바로 부딪혀 평소 규칙대로 가려져요.
                    - **상대 화면**: 빨간 점선과 줄이 날아오는 게 보여요. 맞으면 **키워드 1개를 잃고**, 끌려오는 동안에는 움직이거나 스킬을 쓸 수 없고 다른 원과도 부딪히지 않아요. 옆으로 움직이면 피할 수 있어요.
                    - 끌려온 뒤에는 쓴 사람과 반드시 부딪혀요. 점수가 낮은 쪽이 키워드를 하나 더 잃을 수 있으니, 점수가 높은 상대에게는 조심하세요.""",
                    "🪝 Hook", """
                    **Throw a hook** toward your aim. The circle it hits **loses one keyword and is pulled to you.** See the table above for the unlock level. **It can't be used inside safe zones.**

                    [[skill hook]]

                    - **Aiming**: Toward the mouse on PC; on mobile toward the direction you hold on the joystick (or the direction you were heading). The range is about 5 circle radii, shown first as a dotted line.
                    - **Your screen**: The hook line shoots out, catches the circle and pulls it in. The pulled circle collides with you right away and is settled by the normal rules.
                    - **The other player's screen**: You see a red dotted line and the rope flying at you. If hit you **lose one keyword**, and while being pulled you can't move, use skills, or touch other circles. Move sideways to dodge.
                    - After being pulled you always collide with the user. The lower-scoring side can lose one more keyword, so be careful against higher-scoring players."""),
            new Added("skills", "💥 충격파", """
                    내 원 둘레로 **충격파를 퍼뜨려 닿은 원을 바깥으로 밀어내요.** 키워드는 잃지 않아요. 해금 레벨은 위 표를 보세요. **세이프존 안에서는 쓸 수 없어요.**

                    [[스킬 충격파]]

                    - **언제 쓸까요**: 여러 원에게 둘러싸였을 때 거리를 벌리거나, 가까이 붙은 상대를 떼어 낼 때 좋아요.
                    - **내 화면**: 내 원에서 고리가 퍼져 나가요. 고리의 바깥 점선이 닿는 범위예요. 닿은 원은 바깥으로 밀려나요.
                    - **상대 화면**: 퍼지는 빨간 고리에 닿으면 바깥으로 밀려나요. 키워드를 잃지는 않아요. 고리가 닿기 전에 범위 밖으로 움직이면 피할 수 있어요.
                    - 세이프존 안의 원이나 방패·무적 중인 원은 밀리지 않아요.""",
                    "💥 Shockwave", """
                    **Send a shockwave out around your circle that pushes touched circles outward.** They do not lose keywords. See the table above for the unlock level. **It can't be used inside safe zones.**

                    [[skill shockwave]]

                    - **When to use it**: Open some distance when surrounded by several circles, or shake off someone stuck to you.
                    - **Your screen**: A ring spreads out from your circle. The dotted outer line shows the reach. Circles it touches are pushed outward.
                    - **The other player's screen**: If the red spreading ring touches you, you are pushed outward. You don't lose keywords. Move out of its range before it reaches you to dodge.
                    - Circles in safe zones, or shielded or invulnerable circles, are not pushed."""),
            new Added("skills", "👻 은신", """
                    **잠깐 다른 사람 눈에 배경처럼 안 보여요.** 해금 레벨은 위 표를 보세요. 공격 스킬(갈고리, 충격파)을 쓰면 풀려요.

                    [[스킬 은신]]

                    - **언제 쓸까요**: 큰 원 옆을 몰래 지나가거나, 구슬과 키워드를 노리는 상대의 눈을 피할 때 좋아요.
                    - **내 화면**: 내 원이 반투명으로 보이고 점선 고리와 남은 초가 떠요. 사라질 때는 서서히 옅어지고 고리가 모여들어요.
                    - **상대 화면**: 은신한 원은 서서히 사라져 이름, 테두리, 효과까지 배경과 똑같아 보여요(미니맵에서도 빠져요). 대신 **가까이 오면 내 원 위에 빨간 !**가 떠서 조심하라고 알려 줘요.
                    - 눈에 안 보일 뿐 몸은 그대로라서, 부딪히면 평소처럼 밀리고 키워드를 잃을 수 있어요. 세이프존 안에서는 ! 경고가 뜨지 않아요.""",
                    "👻 Stealth", """
                    **Briefly blend into the background for other players.** See the table above for the unlock level. Using an attack skill (Hook, Shockwave) ends it.

                    [[skill stealth]]

                    - **When to use it**: Sneak past a big circle, or avoid the eyes of someone after the same orbs and keywords.
                    - **Your screen**: Your circle looks faint, with a dashed ring and the seconds left. It fades gradually and the rings gather in as you vanish.
                    - **The other player's screen**: A stealthed circle gradually disappears, name, border and effects included, and looks just like the background (it also drops off the minimap). But **when it comes near, a red ! appears above your circle** to warn you.
                    - You are only unseen: your body is still there, so a collision pushes you as usual and you can lose keywords. The ! warning doesn't appear while you stand in a safe zone.""")
            ,new Added("score-and-size", "죽음과 리스폰", """
                    **키워드가 하나도 없는 채로 충돌에서 지면** 죽어요. 키워드가 있으면 하나를 잃을 뿐이에요.

                    - 세이프존 안에 있거나, 키워드를 잃은 직후 3초 동안은 죽지 않아요.
                    - 죽으면 화면이 흑백으로 어두워지고 누가 쓰러뜨렸는지 알려 줘요. 다른 사람들의 움직임은 계속 구경할 수 있어요.
                    - **리스폰** 버튼을 누르면 가운데 파란 세이프존에서 다시 시작해요. 레벨, 경험치, 칭호, 꾸미기는 그대로예요.
                    - 쓰러뜨린 사람은 경험치를 더 받고(기본 5) **처치** 횟수가 올라가요. 화면에는 처치 카드가 떠요. 처치와 패배 횟수에 따른 칭호도 있어요.
                    - 봇도 같은 규칙으로 쓰러지고, 새 봇이 가운데에서 나와요.

                    > 처치·패배 횟수는 **내 정보**에서 볼 수 있어요.""",
                    "Knockouts and respawning", """
                    If you **lose a collision while holding no keywords**, you are knocked out. If you have keywords, you only lose one.

                    - You can't be knocked out inside a safe zone, or during the 3 seconds right after losing a keyword.
                    - When you are knocked out, the screen fades to black and white and tells you who did it. You can keep watching everyone else move.
                    - Press **Respawn** to start again in the blue safe zone in the middle. Your level, EXP, titles and customization stay.
                    - Whoever knocked you out earns extra EXP (5 by default) and their **takedown** count goes up. A takedown card appears on their screen. There are titles for takedowns and defeats too.
                    - Bots are knocked out by the same rules, and a new bot appears in the middle.

                    > You can see your takedown and defeat counts in **My info**.""")
            ,new Added("arena", "플레이어 검색과 인기도", """
                    **👥 플레이어** 탭(오른쪽 패널, 모바일은 🔍 안의 탭)에서 닉네임 일부로 같은 나라 플레이어를 찾을 수 있어요. 접속 중이 아니어도 찾아져요.

                    - 결과의 이름을 누르면 **플레이어 정보**가 열려요: 현재 유행도, 레벨, **인기도**, **생성일**, 가진 키워드 상위 3개.
                    - **인기도**: 플레이어 정보에서 **+** 또는 **−**를 눌러 올리거나 내려요. 같은 플레이어에게는 **하루에 한 번**만 줄 수 있고(하루는 내 나라 시간 기준), 나 자신에게는 줄 수 없어요. 인기도는 점수나 크기에는 영향이 없어요. 내 인기도는 **내 정보**에서 볼 수 있어요.
                    - **💬 공유**를 누르면 채팅에 그 플레이어가 올라가고, 채팅의 이름을 누르면 누구나 그 정보를 열 수 있어요.
                    - 문제가 있는 플레이어는 **🚩 신고**할 수 있어요.""",
                    "Player search and popularity", """
                    In the **👥 Players** tab (right panel; on mobile, a tab inside 🔍) you can find players in your country by part of a nickname. They are found even when they are offline.

                    - Tap a name in the results to open the **player info**: trend score, level, **popularity**, **creation date**, and their top 3 keywords.
                    - **Popularity**: In the player info, press **+** or **−** to raise or lower it. You can give each player a vote **once a day** (a day follows your country's time), and you can't vote for yourself. Popularity does not affect score or size. You can see your own popularity in **My info**.
                    - Press **💬 Share** and that player appears in chat; anyone can tap the name there to open their info.
                    - You can **🚩 report** players who break the rules.""")
            ,new Added("faq", "새로고침하거나 연결이 끊기면 내 원은 어떻게 되나요?", """
                    나가거나 새로고침하거나 연결이 끊겨도 내 원은 **1분 동안 그 자리에 남아요.** 그 사이 다른 사람이나 봇에게 부딪히면 키워드를 잃을 수 있어요.

                    - 1분 안에 다시 들어오면 그 원을 이어받아요. 단, **콤보와 불꽃은 0부터** 다시 시작해요. 스킬 쿨타임은 그대로 이어져요.
                    - 1분이 지나면 원이 사라지고, 다시 들어오면 가운데 파란 세이프존에서 시작해요.""",
                    "What happens to my orb if I refresh or disconnect?", """
                    If you leave, refresh or lose your connection, your orb **stays where it was for 1 minute.** If another player or a bot hits it in the meantime, you can lose keywords.

                    - If you come back within 1 minute, you take that orb over. Your **combo and flame start again from 0**, but skill cooldowns carry on.
                    - After 1 minute the orb is gone, and when you come back you start in the blue safe zone in the middle."""));
}
