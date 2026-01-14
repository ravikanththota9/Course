CREATE OR REPLACE PROCEDURE SP_UPDATE_NETWORK_END_DATE AS
  -- Cursor for staging rows to process
  CURSOR c_stg IS
    SELECT *
    FROM BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
    WHERE status IS NULL;  -- Process only new rows

  -- Record type for staging
  rec_stg c_stg%ROWTYPE;

  -- Variables for processing
  v_latest_cycle_id pp_prac_net_loc_cycle.id%TYPE;
  v_start_date pp_prac_net_loc_cycle.start_date%TYPE;
  v_prac_id pp_prac_net_cycle.prac_id%TYPE;
  v_net_id pp_prac_net_cycle.net_id%TYPE;
  v_greatest_end_date pp_prac_net_loc_cycle.end_date%TYPE;
  v_solo_id pp_prac_net_loc_cycle.id%TYPE;
  v_solo_end_date pp_prac_net_loc_cycle.end_date%TYPE;

BEGIN
  -- Loop through each staging row
  FOR rec_stg IN c_stg LOOP
    BEGIN
      -- 1. Validate Portico ID
      IF NOT REGEXP_LIKE(rec_stg.portico_id, '^\d{12}$') THEN
        UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
        SET status = 'FAILED: INVALID PORTICO ID'
        WHERE ROWID = rec_stg.ROWID;
        CONTINUE;
      END IF;

      -- 2. Validate loc_id
      IF rec_stg.loc_id IS NULL THEN
        UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
        SET status = 'FAILED: LOC_ID IS NULL'
        WHERE ROWID = rec_stg.ROWID;
        CONTINUE;
      END IF;

      -- 3. Validate new_end_date
      IF rec_stg.new_end_date IS NULL THEN
        UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
        SET status = 'FAILED: NEW_END_DATE IS NULL'
        WHERE ROWID = rec_stg.ROWID;
        CONTINUE;
      END IF;

      -- 4. Find latest cycle in pp_prac_net_loc_cycle
      SELECT c.id, c.start_date, pc.prac_id, pc.net_id
        INTO v_latest_cycle_id, v_start_date, v_prac_id, v_net_id
        FROM pp_prac_net_loc_cycle c
        JOIN v_uda_prac_net_loc_additional v
          ON c.id = v.prac_net_loc_cycle_id
        JOIN pp_prac_net_cycle pc
          ON c.prac_net_cycle_id = pc.id
       WHERE v.portico_id_uda = rec_stg.portico_id
         AND c.loc_id = rec_stg.loc_id
         AND pc.net_id = rec_stg.net_id
       ORDER BY c.end_date DESC
       FETCH FIRST 1 ROW ONLY;

      -- 5. Validate new_end_date > start_date
      IF rec_stg.new_end_date <= v_start_date THEN
        UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
        SET status = 'FAILED: END DATE <= START DATE'
        WHERE ROWID = rec_stg.ROWID;
        CONTINUE;
      END IF;

      -- 6. Update end_date of latest cycle
      UPDATE pp_prac_net_loc_cycle
         SET end_date = rec_stg.new_end_date
       WHERE id = v_latest_cycle_id;

      -- 7. Synchronize Solo cycle if needed
      BEGIN
        -- 7a. Get greatest end_date among all Solo + PPR cycles for this prac_id & net_id
        SELECT MAX(c.end_date)
          INTO v_greatest_end_date
          FROM pp_prac_net_loc_cycle c
          JOIN v_uda_prac_net_loc_additional v
            ON c.id = v.prac_net_loc_cycle_id
          JOIN pp_prac_net_cycle pc
            ON c.prac_net_cycle_id = pc.id
         WHERE pc.prac_id = v_prac_id
           AND pc.net_id = v_net_id;

        -- 7b. Get Solo cycle in pp_prac_net_loc_cycle
        SELECT c.id, c.end_date
          INTO v_solo_id, v_solo_end_date
          FROM pp_prac_net_loc_cycle c
          JOIN v_uda_prac_net_loc_additional v
            ON c.id = v.prac_net_loc_cycle_id
          JOIN pp_prac_net_cycle pc
            ON c.prac_net_cycle_id = pc.id
         WHERE pc.prac_id = v_prac_id
           AND pc.net_id = v_net_id
           AND SUBSTR(v.portico_id_uda, 10, 3) = '001';

        -- 7c. If Solo end_date != greatest end_date → update Solo cycles
        IF v_solo_end_date <> v_greatest_end_date THEN
          -- Update Solo in pp_prac_net_loc_cycle
          UPDATE pp_prac_net_loc_cycle
             SET end_date = v_greatest_end_date
           WHERE id = v_solo_id;

          -- Update Solo in pp_prac_net_cycle
          UPDATE pp_prac_net_cycle
             SET end_date = v_greatest_end_date
           WHERE prac_id = v_prac_id
             AND net_id = v_net_id;
        END IF;

      EXCEPTION
        WHEN NO_DATA_FOUND THEN
          NULL; -- No Solo record found, skip
      END;

      -- 8. Update staging status
      UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
      SET status = 'SUCCESS'
      WHERE ROWID = rec_stg.ROWID;

    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
        SET status = 'FAILED: NO MATCHING CYCLE'
        WHERE ROWID = rec_stg.ROWID;

      WHEN OTHERS THEN
        UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
        SET status = 'FAILED: ' || SQLERRM
        WHERE ROWID = rec_stg.ROWID;
    END;
  END LOOP;

  -- Commit after processing all rows
  COMMIT;

EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK;
    RAISE;
END SP_UPDATE_NETWORK_END_DATE;
/
