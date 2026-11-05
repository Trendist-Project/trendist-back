-- V1 기준선(baseline): 2026-11-05 시점의 JPA 엔티티가 만드는 스키마(37개 테이블).
-- Flyway 도입 전부터 운영 중인 DB는 이 파일을 실행하지 않는다(baseline-version=1로 건너뜀). 새 DB(빈 스키마)에서만 실행된다.
-- 이 파일은 고치지 않는다. 스키마를 바꿀 때는 V2, V3 ... 파일을 새로 추가한다(docs 11.3).

create table chat_messages (
        country_id bigint not null,
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        player_id bigint not null,
        report_count bigint not null,
        body varchar(100) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table comment_votes (
        comment_id bigint not null,
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        player_id bigint not null,
        updated_at DATETIME(6) not null,
        vote varchar(10) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table comments (
        created_at DATETIME(6) not null,
        deleted_at DATETIME(6),
        dislike_count bigint not null,
        id bigint not null auto_increment,
        keyword_id bigint not null,
        like_count bigint not null,
        player_id bigint not null,
        report_count bigint not null,
        updated_at DATETIME(6) not null,
        body varchar(400) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table countries (
        enabled bit not null,
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        max_room_size bigint not null,
        sort_order bigint not null,
        updated_at DATETIME(6) not null,
        code varchar(10) not null,
        default_locale varchar(10) not null,
        timezone varchar(50) not null,
        name varchar(100) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table data_sources (
        weight decimal(6,3) not null,
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        updated_at DATETIME(6) not null,
        name varchar(50) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table effect_types (
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        updated_at DATETIME(6) not null,
        code varchar(20) not null,
        name varchar(50) not null,
        name_en varchar(50),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table effects (
        enabled bit not null,
        created_at DATETIME(6) not null,
        effect_type_id bigint not null,
        id bigint not null auto_increment,
        sort_order bigint not null,
        unlock_value bigint,
        updated_at DATETIME(6) not null,
        unlock_type varchar(20) not null,
        code varchar(30) not null,
        name varchar(100) not null,
        name_en varchar(100),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table guide_categories (
        enabled bit not null,
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        sort_order bigint not null,
        updated_at DATETIME(6) not null,
        display_type varchar(20) not null,
        code varchar(50) not null,
        name varchar(100) not null,
        name_en varchar(100),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table guides (
        created_at DATETIME(6) not null,
        deleted_at DATETIME(6),
        guide_category_id bigint not null,
        id bigint not null auto_increment,
        sort_order bigint not null,
        updated_at DATETIME(6) not null,
        user_id bigint not null,
        title varchar(100) not null,
        title_en varchar(100),
        body MEDIUMTEXT not null,
        body_en MEDIUMTEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table keyword_ai_comments (
        attempts bigint not null,
        commented_at DATETIME(6),
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        updated_at DATETIME(6) not null,
        written_by varchar(10),
        comment varchar(200),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table keyword_ai_requests (
        created_at DATETIME(6) not null,
        handled_at DATETIME(6),
        id bigint not null auto_increment,
        keyword_id bigint not null,
        player_id bigint not null,
        updated_at DATETIME(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table keyword_log_24h (
        hour_slot TINYINT not null,
        created_at DATETIME(6) not null,
        holder_count bigint not null,
        holder_inc bigint not null,
        id bigint not null auto_increment,
        keyword_id bigint not null,
        recorded_at DATETIME not null,
        search_count bigint not null,
        search_inc bigint not null,
        total_plus_count bigint not null,
        total_plus_inc bigint not null,
        updated_at DATETIME(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table keyword_log_dailies (
        record_date date not null,
        created_at DATETIME(6) not null,
        holder_count bigint not null,
        holder_inc bigint not null,
        id bigint not null auto_increment,
        keyword_id bigint not null,
        search_count bigint not null,
        search_inc bigint not null,
        total_plus_count bigint not null,
        total_plus_inc bigint not null,
        updated_at DATETIME(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table keyword_states (
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        updated_at DATETIME(6) not null,
        name varchar(50) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table keywords (
        activated_at DATETIME(6),
        ai_comment_id bigint,
        banned_at DATETIME(6),
        country_id bigint not null,
        created_at DATETIME(6) not null,
        data_source_id bigint not null,
        external_until DATETIME(6),
        holder_count bigint not null,
        id bigint not null auto_increment,
        inactive_at DATETIME(6),
        keyword_state_id bigint not null,
        search_count bigint not null,
        total_plus_count bigint not null,
        trend_value bigint not null,
        updated_at DATETIME(6) not null,
        text varchar(20) not null,
        normalized varchar(100) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table levels (
        badge_rainbow bit not null,
        badge_color varchar(7),
        badge_alpha bigint,
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        level bigint not null,
        required_exp bigint not null,
        updated_at DATETIME(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table notices (
        pinned bit not null,
        popup_enabled bit not null,
        created_at DATETIME(6) not null,
        deleted_at DATETIME(6),
        id bigint not null auto_increment,
        popup_until DATETIME(6),
        updated_at DATETIME(6) not null,
        user_id bigint not null,
        title varchar(100) not null,
        title_en varchar(100),
        body TEXT not null,
        body_en TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table player_keywords (
        created_at DATETIME(6) not null,
        expires_at DATETIME(6) not null,
        id bigint not null auto_increment,
        keyword_id bigint not null,
        player_id bigint not null,
        updated_at DATETIME(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table player_positions (
        x float(53) not null,
        y float(53) not null,
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        player_id bigint not null,
        saved_at DATETIME(6) not null,
        updated_at DATETIME(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table player_score_24h (
        hour_slot TINYINT not null,
        created_at DATETIME(6) not null,
        current_score bigint not null,
        id bigint not null auto_increment,
        player_id bigint not null,
        recorded_at DATETIME not null,
        updated_at DATETIME(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table player_score_dailies (
        record_date date not null,
        created_at DATETIME(6) not null,
        current_score bigint not null,
        id bigint not null auto_increment,
        player_id bigint not null,
        updated_at DATETIME(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table player_titles (
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        player_id bigint not null,
        title_id bigint not null,
        updated_at DATETIME(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table players (
        active_days BIGINT NOT NULL DEFAULT 0 not null,
        bot_win_count BIGINT NOT NULL DEFAULT 0 not null,
        comment_score bigint not null,
        country_id bigint not null,
        created_at DATETIME(6) not null,
        current_score bigint not null,
        exp bigint not null,
        hold_count bigint not null,
        id bigint not null auto_increment,
        last_played_at DATETIME(6),
        last_seen_at DATETIME(6),
        level_id bigint not null,
        lose_count BIGINT NOT NULL DEFAULT 0 not null,
        nickname_changed_at DATETIME(6),
        orb_count BIGINT NOT NULL DEFAULT 0 not null,
        search_count BIGINT NOT NULL DEFAULT 0 not null,
        steal_count BIGINT NOT NULL DEFAULT 0 not null,
        title_id bigint,
        total_plus_count bigint not null,
        updated_at DATETIME(6) not null,
        user_id bigint not null,
        nickname varchar(40) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table profiles (
        equipped bit not null,
        background_color varchar(7) not null,
        border_color varchar(7),
        name_color varchar(7),
        border_effect_id bigint,
        border_width bigint,
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        image_changed_at DATETIME(6),
        player_id bigint not null,
        slot bigint not null,
        trail_effect_id bigint,
        updated_at DATETIME(6) not null,
        name_style varchar(20),
        profile_type varchar(20) not null,
        pixel_palette varchar(300),
        pixel_data varchar(1024),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table refresh_tokens (
        created_at DATETIME(6) not null,
        expires_at DATETIME(6) not null,
        id bigint not null auto_increment,
        revoked_at DATETIME(6),
        updated_at DATETIME(6) not null,
        user_id bigint not null,
        token_hash varchar(64) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table report_types (
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        updated_at DATETIME(6) not null,
        name varchar(50) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table reports (
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        player_id bigint not null,
        report_type_id bigint not null,
        target_id bigint not null,
        updated_at DATETIME(6) not null,
        status varchar(20) not null,
        reason varchar(500),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table room_states (
        active_area float(53) not null,
        country_id bigint not null,
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        saved_at DATETIME(6) not null,
        updated_at DATETIME(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table sanction_types (
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        updated_at DATETIME(6) not null,
        name varchar(100) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table sanctions (
        created_at DATETIME(6) not null,
        expires_at DATETIME(6) not null,
        id bigint not null auto_increment,
        released_at DATETIME(6),
        sanction_type_id bigint not null,
        updated_at DATETIME(6) not null,
        user_id bigint not null,
        reason varchar(500) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table settings (
        max_value decimal(18,6),
        min_value decimal(18,6),
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        updated_at DATETIME(6) not null,
        value_type varchar(20) not null,
        name varchar(100) not null,
        description varchar(500),
        value varchar(2000) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table share_cards (
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        play_count BIGINT DEFAULT 0 not null,
        player_id bigint not null,
        updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) not null,
        view_count BIGINT DEFAULT 0 not null,
        code varchar(16) not null,
        content_hash CHAR(64) not null,
        data JSON not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table site_daily_stats (
        record_date date not null,
        active_players bigint not null,
        chat_count bigint not null,
        collision_count bigint not null,
        comment_count bigint not null,
        country_id bigint not null,
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        new_players bigint not null,
        orb_count bigint not null,
        peak_online bigint not null,
        plus_count bigint not null,
        search_count bigint not null,
        share_count bigint not null,
        updated_at DATETIME(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table site_hourly_stats (
        avg_online DECIMAL(10,2) not null,
        active_players bigint not null,
        chat_count bigint not null,
        collision_count bigint not null,
        comment_count bigint not null,
        country_id bigint not null,
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        new_players bigint not null,
        orb_count bigint not null,
        peak_online bigint not null,
        plus_count bigint not null,
        recorded_at DATETIME(6) not null,
        search_count bigint not null,
        share_count bigint not null,
        updated_at DATETIME(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table titles (
        enabled bit not null,
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        sort_order bigint not null,
        updated_at DATETIME(6) not null,
        condition_type varchar(20) not null,
        icon varchar(100),
        name varchar(100) not null,
        name_en varchar(100),
        description varchar(500) not null,
        description_en varchar(500),
        condition_params JSON not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table user_states (
        created_at DATETIME(6) not null,
        id bigint not null auto_increment,
        updated_at DATETIME(6) not null,
        name varchar(50) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table users (
        banned_until DATETIME(6),
        country_id bigint,
        created_at DATETIME(6) not null,
        deleted_at DATETIME(6),
        id bigint not null auto_increment,
        last_login_at DATETIME(6),
        tokens_revoked_at DATETIME(6),
        updated_at DATETIME(6) not null,
        upload_banned_until DATETIME(6),
        user_state_id bigint not null,
        ui_locale varchar(10) not null,
        oauth_type varchar(20),
        role varchar(20) not null,
        visitor_id varchar(51),
        oauth_id varchar(64),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create index idx_chat_messages_room 
       on chat_messages (country_id, created_at);

    create index idx_chat_messages_created 
       on chat_messages (created_at);

    alter table comment_votes 
       add constraint uk_comment_votes unique (comment_id, player_id);

    create index idx_comments_latest 
       on comments (keyword_id, created_at, id);

    create index idx_comments_top 
       on comments (keyword_id, deleted_at, like_count, created_at);

    create index idx_comments_user 
       on comments (player_id);

    alter table countries 
       add constraint uk_countries_code unique (code);

    alter table data_sources 
       add constraint uk_data_sources_name unique (name);

    alter table effect_types 
       add constraint uk_effect_types_code unique (code);

    alter table effects 
       add constraint uk_effects unique (effect_type_id, code);

    alter table guide_categories 
       add constraint uk_guide_categories_code unique (code);

    create index idx_guides_list 
       on guides (guide_category_id, deleted_at, sort_order);

    create index idx_kw_ai_req_keyword 
       on keyword_ai_requests (keyword_id, handled_at);

    create index idx_kw_ai_req_player 
       on keyword_ai_requests (player_id, keyword_id);

    alter table keyword_log_24h 
       add constraint uk_keyword_log_24h unique (keyword_id, hour_slot);

    create index idx_keyword_log_dailies_date 
       on keyword_log_dailies (record_date);

    alter table keyword_log_dailies 
       add constraint uk_keyword_log_dailies unique (keyword_id, record_date);

    alter table keyword_states 
       add constraint uk_keyword_states_name unique (name);

    create index idx_keywords_state 
       on keywords (country_id, keyword_state_id, trend_value);

    alter table keywords 
       add constraint uk_keywords_country_normalized unique (country_id, normalized);

    alter table keywords 
       add constraint UKe8dtbu1nv9atgr19bptfm1i0k unique (ai_comment_id);

    alter table levels 
       add constraint uk_levels_level unique (level);

    create index idx_notices_list 
       on notices (deleted_at, pinned, created_at);

    create index idx_notices_popup 
       on notices (deleted_at, popup_enabled, popup_until);

    create index idx_player_keywords_keyword 
       on player_keywords (keyword_id);

    create index idx_player_keywords_expires 
       on player_keywords (expires_at);

    alter table player_keywords 
       add constraint uk_player_keywords unique (player_id, keyword_id);

    create index idx_player_positions_saved 
       on player_positions (saved_at);

    alter table player_positions 
       add constraint uk_player_positions_player unique (player_id);

    alter table player_score_24h 
       add constraint uk_player_score_24h unique (player_id, hour_slot);

    alter table player_score_dailies 
       add constraint uk_player_score_dailies unique (player_id, record_date);

    alter table player_titles 
       add constraint uk_player_titles unique (player_id, title_id);

    create index idx_players_ranking 
       on players (country_id, current_score);

    alter table players 
       add constraint uk_players unique (user_id, country_id);

    create index idx_profiles_player 
       on profiles (player_id, equipped);

    alter table profiles 
       add constraint uk_profiles unique (player_id, slot);

    create index idx_refresh_tokens_user 
       on refresh_tokens (user_id, revoked_at);

    create index idx_refresh_tokens_expires 
       on refresh_tokens (expires_at);

    alter table refresh_tokens 
       add constraint uk_refresh_tokens_hash unique (token_hash);

    alter table report_types 
       add constraint uk_report_types_name unique (name);

    create index idx_reports_status 
       on reports (status, created_at);

    alter table reports 
       add constraint uk_reports_once unique (report_type_id, target_id, player_id);

    alter table room_states 
       add constraint uk_room_states_country unique (country_id);

    alter table sanction_types 
       add constraint uk_sanction_types_name unique (name);

    create index idx_sanctions_user 
       on sanctions (user_id, sanction_type_id, created_at);

    alter table settings 
       add constraint uk_settings_name unique (name);

    alter table share_cards 
       add constraint uk_share_cards_code unique (code);

    alter table share_cards 
       add constraint uk_share_cards_player_hash unique (player_id, content_hash);

    alter table site_daily_stats 
       add constraint uk_site_daily_stats unique (country_id, record_date);

    alter table site_hourly_stats 
       add constraint uk_site_hourly_stats unique (country_id, recorded_at);

    alter table titles 
       add constraint uk_titles_name unique (name);

    alter table user_states 
       add constraint uk_user_states_name unique (name);

    alter table users 
       add constraint uk_users_visitor_id unique (visitor_id);

    alter table users 
       add constraint uk_users_oauth unique (oauth_type, oauth_id);

    alter table chat_messages 
       add constraint FKselwi78t73anp2jx845jff7ig 
       foreign key (country_id) 
       references countries (id);

    alter table chat_messages 
       add constraint FK4dgsf2hb2p3y6nmp14hgoiwqr 
       foreign key (player_id) 
       references players (id);

    alter table comment_votes 
       add constraint FKhdta06oo0umsl3usqm21ra5j8 
       foreign key (comment_id) 
       references comments (id);

    alter table comment_votes 
       add constraint FKtprwgkjuj3l07jqcqujrwr2t2 
       foreign key (player_id) 
       references players (id);

    alter table comments 
       add constraint FK9sn8i42eo2b0rbfoarpo4y5qd 
       foreign key (keyword_id) 
       references keywords (id);

    alter table comments 
       add constraint FKpvqmq5bx4lot3r2ou9dbfgnk8 
       foreign key (player_id) 
       references players (id);

    alter table effects 
       add constraint FK70uhm1wm5eedi3fiebrfktp36 
       foreign key (effect_type_id) 
       references effect_types (id);

    alter table guides 
       add constraint FKhvcxa94ho8kwedwss1g3n6u2u 
       foreign key (guide_category_id) 
       references guide_categories (id);

    alter table guides 
       add constraint FK9epk7rkhp13yrdhou2838dgam 
       foreign key (user_id) 
       references users (id);

    alter table keyword_log_24h 
       add constraint FKdlcxvkxb6na50g6b9grvbgefu 
       foreign key (keyword_id) 
       references keywords (id);

    alter table keyword_log_dailies 
       add constraint FKr7vl458uq60y1e0gjhpf4lf0a 
       foreign key (keyword_id) 
       references keywords (id);

    alter table keywords 
       add constraint FK4eae3ahut6mqhaxdbn985osvf 
       foreign key (ai_comment_id) 
       references keyword_ai_comments (id) 
       on delete set null;

    alter table keywords 
       add constraint FK5s9wgo2rttrem37b4lahkny6w 
       foreign key (country_id) 
       references countries (id);

    alter table keywords 
       add constraint FKeeaxu5g5g5b4pg5n7kg3poaxy 
       foreign key (data_source_id) 
       references data_sources (id);

    alter table keywords 
       add constraint FK9h3ugy631wqrxytf5ap3pmpn5 
       foreign key (keyword_state_id) 
       references keyword_states (id);

    alter table notices 
       add constraint FKerqkxcii6jcl64muyrcxm10sp 
       foreign key (user_id) 
       references users (id);

    alter table player_keywords 
       add constraint FKt8yreemyn3pvssuc9lfq86aux 
       foreign key (keyword_id) 
       references keywords (id);

    alter table player_keywords 
       add constraint FKl09uvylq405arubo52q5b47w2 
       foreign key (player_id) 
       references players (id);

    alter table player_score_24h 
       add constraint FK5rddmu61s8ka7utyk6q1tg3v6 
       foreign key (player_id) 
       references players (id);

    alter table player_score_dailies 
       add constraint FKknvwj6wq8x6762xf71rtiev48 
       foreign key (player_id) 
       references players (id);

    alter table players 
       add constraint FKqntvf7pxlrop31khocahqiclo 
       foreign key (country_id) 
       references countries (id);

    alter table players 
       add constraint fk_players_level 
       foreign key (level_id) 
       references levels (id);

    alter table players 
       add constraint FK3rfv9832bif6rea5edetib8it 
       foreign key (user_id) 
       references users (id);

    alter table refresh_tokens 
       add constraint FK1lih5y2npsf8u5o3vhdb9y0os 
       foreign key (user_id) 
       references users (id);

    alter table reports 
       add constraint FK48w8drb34twehp22npgpaqhfx 
       foreign key (player_id) 
       references players (id);

    alter table reports 
       add constraint FKo91x31sier8wrdto7u6paik7o 
       foreign key (report_type_id) 
       references report_types (id);

    alter table sanctions 
       add constraint FKs2wa8kagrhn97yd21gm5viwa1 
       foreign key (sanction_type_id) 
       references sanction_types (id);

    alter table sanctions 
       add constraint FK2rl1qgp05luq1fc7aorbnpo0t 
       foreign key (user_id) 
       references users (id);

    alter table share_cards 
       add constraint FKpfkr0jov7yqc3tg2a8ixron5a 
       foreign key (player_id) 
       references players (id);

    alter table users 
       add constraint FKjlpks00ofkq3sqd9hqiavv5lg 
       foreign key (country_id) 
       references countries (id);

    alter table users 
       add constraint FKmmi11gs4jb9rlm3dbqx37qs8x 
       foreign key (user_state_id) 
       references user_states (id);
