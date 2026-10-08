-- pg-simulator(e2e 프로파일)가 쓰는 DB. 커머스(loopers)와 나눈다.
-- 같은 DB 를 쓰면 pg-simulator 의 ddl-auto: create 가 커머스 테이블까지 지운다 (apps/pg-simulator application.yml 참고)
CREATE DATABASE IF NOT EXISTS paymentgateway CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
GRANT ALL PRIVILEGES ON paymentgateway.* TO 'application'@'%';
FLUSH PRIVILEGES;
