-- Tech English local MySQL 8 bootstrap.
-- Run as a MySQL administrator:
--   mysql -u root -p < scripts/init-mysql.sql
-- Change the password before using this outside a local machine.

CREATE DATABASE IF NOT EXISTS tech_english
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'tech_english'@'localhost'
  IDENTIFIED BY 'change-me';

GRANT ALL PRIVILEGES ON tech_english.* TO 'tech_english'@'localhost';
FLUSH PRIVILEGES;
