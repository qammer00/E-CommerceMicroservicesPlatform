CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_reference VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    method VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_payments_payment_reference UNIQUE (payment_reference)
);

CREATE INDEX idx_payments_order_id ON payments (order_id);
CREATE INDEX idx_payments_user_id ON payments (user_id);
CREATE INDEX idx_payments_status ON payments (status);
