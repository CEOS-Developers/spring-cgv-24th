INSERT INTO cgv_db.movies (release_date, running_time, movie_id, poster_url, description, title, content_rating_code) VALUES ('2026-08-05', 172, 1, 'https://cdn.cgv.co.kr/cgvpomsfilm/Movie/Thumbnail/Poster/030001/30001323/30001323_320.jpg', '이 시대 영화계 최고의 거장 크리스토퍼 놀란 감독의 새로운 신화
인류 최고의 고전 [오디세이아]가 스크린에 펼쳐진다!

10년간 이어진 트로이 전쟁을 승리로 이끈 영웅 \'오디세우스\'(맷 데이먼)는
왕의 부재를 틈타 침탈과 권력 다툼이 벌어진 왕국에서
그를 기다리고 있는 아내 \'페넬로페\'(앤 해서웨이)와
아들 \'텔레마코스\'(톰 홀랜드)에게 돌아가기 위한 여정에 나선다.
그러나 신들의 분노를 산 그의 귀환 앞에는 거대한 폭풍과 괴물들,
그리고 거스를 수 없는 운명의 시련이 기다리고 있는데…

"누구도 나의 귀향을 막을 수 없어. 신들조차도"', '오디세이', 'RATE_15');
INSERT INTO cgv_db.movies (release_date, running_time, movie_id, poster_url, description, title, content_rating_code) VALUES ('2026-09-16', 132, 2, 'https://cdn.cgv.co.kr/cgvpomsfilm/Movie/Thumbnail/Poster/030001/30001399/30001399_320.jpg', '올가을, 다시 출근합니다

창업 3년 만에 100억대 매출을 달성하며 브랜드 ‘WOO22’(우투투)를
패션 업계의 다크호스로 성장시킨 젊은 CEO ‘선우’(한소희).
성공을 향해 쉼 없이 달려온 열정 과부하 상태의 그녀 앞에
막내로 입사한 실버 인턴 ‘기호’(최민식)가 나타난다.

경력 37년 사회생활 만렙의 베테랑이지만,
낯선 디지털 업무 환경과 자유분방한 문화에 좀처럼 적응하지 못하던 ‘기호’.
그러나 특유의 성실함과 인간미 넘치는 소통이 ‘선우’의 마음을 움직이고
모든 점에서 전혀 다른 두 사람은 서로에게 없는 시선과 경험을 나누며
쉼 없이 달리던 일상에 뜻밖의 온기를 더하는데…', '인턴', 'RATE_12');
INSERT INTO cgv_db.movies (release_date, running_time, movie_id, poster_url, description, title, content_rating_code) VALUES ('2026-09-23', 131, 3, 'https://cdn.cgv.co.kr/cgvpomsfilm/Movie/Thumbnail/Poster/030001/30001314/30001314_320.jpg', '1974년 8월 15일,
대한민국을 충격에 빠뜨린 영부인 저격사건의
의혹과 배후를 추적하는 이야기', '암살자(들)', 'RATE_12');

INSERT INTO cgv_db.genres (genre_id, name) VALUES (1, '액션');
INSERT INTO cgv_db.genres (genre_id, name) VALUES (2, '드라마');
INSERT INTO cgv_db.genres (genre_id, name) VALUES (3, '어드벤처');
INSERT INTO cgv_db.genres (genre_id, name) VALUES (4, '범죄');

INSERT INTO cgv_db.movie_genres (genre_id, movie_genre_id, movie_id) VALUES (1, 1, 1);
INSERT INTO cgv_db.movie_genres (genre_id, movie_genre_id, movie_id) VALUES (2, 2, 1);
INSERT INTO cgv_db.movie_genres (genre_id, movie_genre_id, movie_id) VALUES (3, 3, 1);
INSERT INTO cgv_db.movie_genres (genre_id, movie_genre_id, movie_id) VALUES (2, 4, 2);
INSERT INTO cgv_db.movie_genres (genre_id, movie_genre_id, movie_id) VALUES (2, 5, 3);
INSERT INTO cgv_db.movie_genres (genre_id, movie_genre_id, movie_id) VALUES (4, 6, 3);

INSERT INTO cgv_db.regions (region_id, name) VALUES (1, '서울');
INSERT INTO cgv_db.regions (region_id, name) VALUES (2, '경기');
INSERT INTO cgv_db.regions (region_id, name) VALUES (3, '인천');
INSERT INTO cgv_db.regions (region_id, name) VALUES (4, '강원');
INSERT INTO cgv_db.regions (region_id, name) VALUES (5, '대전/충청');
INSERT INTO cgv_db.regions (region_id, name) VALUES (6, '대구');
INSERT INTO cgv_db.regions (region_id, name) VALUES (7, '부산/울산');
INSERT INTO cgv_db.regions (region_id, name) VALUES (8, '경상');
INSERT INTO cgv_db.regions (region_id, name) VALUES (9, '광주/전라/제주');

INSERT INTO cgv_db.cinemas (cinema_id, region_id, name, address) VALUES (1, 1, '강남', '서울특별시 강남구 강남대로 438 스타플렉스 4');
INSERT INTO cgv_db.cinemas (cinema_id, region_id, name, address) VALUES (2, 1, '압구정', '서울특별시 강남구 압구정로30길 45');
INSERT INTO cgv_db.cinemas (cinema_id, region_id, name, address) VALUES (3, 2, '광교', '경기도 수원시 영통구 광교호수공원로 320');

INSERT INTO cgv_db.screen_types (column_count, row_count, screen_type_id, name) VALUES (7, 8, 1, '일반관');
INSERT INTO cgv_db.screen_types (column_count, row_count, screen_type_id, name) VALUES (12, 12, 2, '특별관');

INSERT INTO cgv_db.screens (cinema_id, screen_id, screen_type_id, name) VALUES (1, 1, 1, '1관');
INSERT INTO cgv_db.screens (cinema_id, screen_id, screen_type_id, name) VALUES (1, 2, 2, '2관');

INSERT INTO cgv_db.users (created_at, user_id, name, email, password) VALUES ('2026-09-16 22:59:24.000000', 1, '이나경', 'rinarina0429@ewha.ac.kr', 'rina');

INSERT INTO cgv_db.showtimes (movie_id, screen_id, showtime_id, start_time) VALUES (1, 1, 1, '2026-09-16 23:00:15.000000');
INSERT INTO cgv_db.showtimes (movie_id, screen_id, showtime_id, start_time) VALUES (2, 2, 2, '2026-08-16 23:00:24.000000');
