-- 실험 2. 재고 단건 조회와 IN 일괄 조회의 실행 계획 비교 (cgv_index_lab에서 실행)
USE cgv_index_lab;
DROP TABLE IF EXISTS stock;
-- 실제 스키마와 같은 인덱스 구성 (uk_stock_theater_item, item_id FK 인덱스)
CREATE TABLE stock (
  quantity int NOT NULL,
  id bigint NOT NULL AUTO_INCREMENT,
  item_id bigint NOT NULL,
  theater_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stock_theater_item (theater_id, item_id),
  KEY idx_item_id (item_id)
) ENGINE=InnoDB;

SET SESSION cte_max_recursion_depth = 10000;
-- 영화관 200곳 × 상품 30종 = 6,000건
INSERT INTO stock (quantity, item_id, theater_id)
WITH RECURSIVE t (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM t WHERE n < 200),
               i (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM i WHERE n < 30)
SELECT 100, i.n, t.n FROM t CROSS JOIN i;
ANALYZE TABLE stock;

-- Before: 상품마다 한 번씩 실행
EXPLAIN SELECT s1_0.id,s1_0.item_id,s1_0.quantity,s1_0.theater_id FROM stock s1_0 WHERE s1_0.theater_id=17 AND s1_0.item_id=3;
EXPLAIN ANALYZE SELECT s1_0.id,s1_0.item_id,s1_0.quantity,s1_0.theater_id FROM stock s1_0 WHERE s1_0.theater_id=17 AND s1_0.item_id=3;

-- After: 상품 10종을 한 번에 조회
EXPLAIN SELECT s1_0.id,s1_0.item_id,s1_0.quantity,s1_0.theater_id FROM stock s1_0 WHERE s1_0.theater_id=17 AND s1_0.item_id IN (1,3,5,7,9,11,13,15,17,19);
EXPLAIN ANALYZE SELECT s1_0.id,s1_0.item_id,s1_0.quantity,s1_0.theater_id FROM stock s1_0 WHERE s1_0.theater_id=17 AND s1_0.item_id IN (1,3,5,7,9,11,13,15,17,19);

-- After: 상품 1종
EXPLAIN SELECT s1_0.id,s1_0.item_id,s1_0.quantity,s1_0.theater_id FROM stock s1_0 WHERE s1_0.theater_id=17 AND s1_0.item_id IN (3);
