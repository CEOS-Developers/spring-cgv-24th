-- 실험 1 부가 측정: 인덱스 유무에 따른 단건 INSERT / status UPDATE 비용 (cgv_index_lab에서 setup 이후 실행)
USE cgv_index_lab;
DROP TABLE IF EXISTS w_without, w_with;
CREATE TABLE w_without LIKE reservation;
ALTER TABLE w_without DROP INDEX idx_reservation_status_expires_at;
CREATE TABLE w_with LIKE reservation;
INSERT INTO w_without SELECT * FROM reservation;
INSERT INTO w_with SELECT * FROM reservation;

DROP PROCEDURE IF EXISTS write_bench;
DELIMITER //
CREATE PROCEDURE write_bench(IN tbl VARCHAR(20), IN cnt INT)
BEGIN
  DECLARE i INT DEFAULT 0;
  DECLARE t0 DATETIME(6);
  SET @ins = CONCAT('INSERT INTO ', tbl, ' (total_price, reserved_at, schedule_id, user_id, seat_summary, status, expires_at) ',
                    'VALUES (15000, NOW(6), 1, 1, ''A1'', ''PENDING_PAYMENT'', NOW(6) + INTERVAL 5 MINUTE)');
  SET @upd = CONCAT('UPDATE ', tbl, ' SET status = ''EXPIRED'' WHERE id = ?');
  PREPARE ins FROM @ins;
  PREPARE upd FROM @upd;

  SET t0 = NOW(6);
  START TRANSACTION;
  WHILE i < cnt DO EXECUTE ins; SET i = i + 1; END WHILE;
  COMMIT;
  SET @insert_ms = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6)) / 1000;
  SET @base = LAST_INSERT_ID() - cnt;

  SET i = 0; SET t0 = NOW(6);
  START TRANSACTION;
  WHILE i < cnt DO SET @id = @base + 1 + i; EXECUTE upd USING @id; SET i = i + 1; END WHILE;
  COMMIT;
  SET @update_ms = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6)) / 1000;

  DEALLOCATE PREPARE ins; DEALLOCATE PREPARE upd;
  SET @cq = CONCAT('SELECT COUNT(*) INTO @updated FROM ', tbl, ' WHERE id > ', @base, ' AND status = ''EXPIRED''');
  PREPARE c FROM @cq; EXECUTE c; DEALLOCATE PREPARE c;
  SELECT tbl, cnt, @insert_ms AS insert_ms, @update_ms AS update_ms, @updated AS updated_rows;
END //
DELIMITER ;
