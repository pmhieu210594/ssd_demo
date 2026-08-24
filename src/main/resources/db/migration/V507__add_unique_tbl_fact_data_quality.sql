ALTER TABLE tbl_fact_data_quality
DROP CONSTRAINT IF EXISTS unique_tbl_fact_data_quality;

ALTER TABLE tbl_fact_data_quality ADD CONSTRAINT unique_tbl_fact_data_quality UNIQUE ( 
    project_id
    , repository_id
    , source_type
    , source_ref
);