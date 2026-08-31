-- ============================================================================
-- V5: Create Failed Tasks Table for Asynchronous Retries & Dead Letter Queue
-- ============================================================================

CREATE TABLE IF NOT EXISTS failed_tasks (
    id UUID NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    updated_by UUID,
    task_type VARCHAR(255) NOT NULL,
    reference_id UUID,
    description TEXT,
    payload TEXT NOT NULL,
    last_error TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    last_attempted_at TIMESTAMP WITH TIME ZONE,
    retry_after TIMESTAMP WITH TIME ZONE,
    status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_failed_tasks PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_failed_task_type ON failed_tasks (task_type);
CREATE INDEX IF NOT EXISTS idx_failed_task_status ON failed_tasks (status);
CREATE INDEX IF NOT EXISTS idx_failed_task_reference ON failed_tasks (reference_id);
