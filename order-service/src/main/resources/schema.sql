CREATE TABLE IF NOT EXISTS orders (
  order_no VARCHAR(36) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  client_key VARCHAR(64) NOT NULL,
  product_id BIGINT NOT NULL,
  product_name VARCHAR(100) NOT NULL,
  quantity INT NOT NULL,
  unit_price_cents BIGINT NOT NULL,
  total_cents BIGINT NOT NULL,
  pickup_point_id BIGINT NOT NULL,
  pickup_point_name VARCHAR(100) NOT NULL,
  status VARCHAR(12) NOT NULL,
  pickup_code VARCHAR(12) UNIQUE,
  stock_released TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME NOT NULL,
  UNIQUE KEY uk_user_client (user_id,client_key),
  KEY idx_status_expire (status,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
