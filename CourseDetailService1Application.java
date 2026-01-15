CREATE OR REPLACE PROCEDURE sp_bcbsmi_update_term_nwk_end_dt_all
AS
  CURSOR c_stg IS
    SELECT ROWID AS rid,
           portico_id,
           net_id,
           loc_id,
           current_start_date,
           current_end_date,
           new_end_date
      FROM BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
     WHERE status IS NULL;

  -- Common
  v_err_msg VARCHAR2(4000);
  v_new_end_date DATE;

  -- Practitioner vars
  v_prac_id               pp_prac_net_cycle.prac_id%TYPE;
  v_prac_net_cycle_id     pp_prac_net_cycle.id%TYPE;
  v_prac_loc_cycle_id     pp_prac_net_loc_cycle.id%TYPE;
  v_prac_start_date       DATE;
  v_prac_curr_start_date  DATE;
  v_prac_next_start_date  DATE;
  v_prac_solo_id          pp_prac_net_loc_cycle.id%TYPE;
  v_prac_solo_end_date    DATE;
  v_prac_greatest_end_dt  DATE;

  -- Provider vars
  v_prov_id               v_uda_prov_facets.prov_id%TYPE;
  v_prov_net_cycle_id     pp_prov_net_cycle.id%TYPE;
  v_prov_loc_cycle_id     pp_prov_net_loc_cyle.id%TYPE;
  v_prov_start_date       DATE;
  v_prov_curr_end_date    DATE;
  v_prov_next_start_date  DATE;
  v_prov_greatest_end_dt  DATE;
  v_prov_latest_cycle_id  pp_prov_net_cycle.id%TYPE;

BEGIN
  FOR rec_stg IN c_stg LOOP
    BEGIN
      ----------------------------------------------------------
      -- Common validations
      ----------------------------------------------------------
      IF rec_stg.portico_id IS NULL THEN
        UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
           SET status = 'FAILED: PORTICO IS NULL'
         WHERE ROWID = rec_stg.rid;
        CONTINUE;
      END IF;

      IF rec_stg.loc_id IS NULL THEN
        UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
           SET status = 'FAILED: LOC_ID IS NULL'
         WHERE ROWID = rec_stg.rid;
        CONTINUE;
      END IF;

      IF rec_stg.new_end_date IS NULL
         OR NOT REGEXP_LIKE(rec_stg.new_end_date,'^\d{1,2}/\d{1,2}/\d{4}$') THEN
        UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
           SET status = 'FAILED: INVALID NEW END DATE'
         WHERE ROWID = rec_stg.rid;
        CONTINUE;
      END IF;

      v_new_end_date := TO_DATE(rec_stg.new_end_date,'MM/DD/YYYY');

      ----------------------------------------------------------
      -- PROVIDER LOGIC
      ----------------------------------------------------------
      IF SUBSTR(rec_stg.portico_id,1,1) = 'G' THEN

        IF rec_stg.current_end_date IS NULL
           OR NOT REGEXP_LIKE(rec_stg.current_end_date,'^\d{1,2}/\d{1,2}/\d{4}$') THEN
          UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
             SET status = 'FAILED: INVALID CURRENT END DATE'
           WHERE ROWID = rec_stg.rid;
          CONTINUE;
        END IF;

        v_prov_curr_end_date := TO_DATE(rec_stg.current_end_date,'MM/DD/YYYY');

        SELECT prov_id
          INTO v_prov_id
          FROM v_uda_prov_facets
         WHERE portico_id_uda = rec_stg.portico_id;

        SELECT plc.id, plc.start_date, plc.prov_net_cycle_id
          INTO v_prov_loc_cycle_id, v_prov_start_date, v_prov_net_cycle_id
          FROM pp_prov_net_loc_cyle plc
          JOIN pp_prov_net_cycle pc ON pc.id = plc.prov_net_cycle_id
         WHERE plc.prov_id = v_prov_id
           AND pc.net_id = rec_stg.net_id
           AND plc.loc_id = rec_stg.loc_id
           AND plc.end_date = v_prov_curr_end_date;

        IF v_new_end_date <= v_prov_start_date THEN
          UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
             SET status = 'FAILED: END DATE <= START DATE'
           WHERE ROWID = rec_stg.rid;
          CONTINUE;
        END IF;

        SELECT MIN(start_date)
          INTO v_prov_next_start_date
          FROM pp_prov_net_loc_cyle
         WHERE prov_net_cycle_id = v_prov_net_cycle_id
           AND loc_id = rec_stg.loc_id
           AND start_date > v_prov_start_date;

        IF v_prov_next_start_date IS NOT NULL
           AND v_new_end_date >= v_prov_next_start_date THEN
          UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
             SET status = 'FAILED: END DATE OVERLAPS NEXT CYCLE'
           WHERE ROWID = rec_stg.rid;
          CONTINUE;
        END IF;

        UPDATE pp_prov_net_loc_cyle
           SET end_date = v_new_end_date
         WHERE id = v_prov_loc_cycle_id;

        SELECT MAX(end_date)
          INTO v_prov_greatest_end_dt
          FROM pp_prov_net_loc_cyle
         WHERE prov_id = v_prov_id
           AND loc_id = rec_stg.loc_id;

        SELECT id
          INTO v_prov_latest_cycle_id
          FROM pp_prov_net_cycle
         WHERE prov_id = v_prov_id
           AND net_id = rec_stg.net_id
           AND start_date =
               (SELECT MAX(start_date)
                  FROM pp_prov_net_cycle
                 WHERE prov_id = v_prov_id
                   AND net_id = rec_stg.net_id);

        UPDATE pp_prov_net_cycle
           SET end_date = v_prov_greatest_end_dt
         WHERE id = v_prov_latest_cycle_id;

      ----------------------------------------------------------
      -- PRACTITIONER LOGIC
      ----------------------------------------------------------
      ELSE

        IF NOT REGEXP_LIKE(rec_stg.portico_id,'^\d{12}$') THEN
          UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
             SET status = 'FAILED: INVALID PRACT PORTICO'
           WHERE ROWID = rec_stg.rid;
          CONTINUE;
        END IF;

        IF rec_stg.current_start_date IS NULL
           OR NOT REGEXP_LIKE(rec_stg.current_start_date,'^\d{1,2}/\d{1,2}/\d{4}$') THEN
          UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
             SET status = 'FAILED: INVALID CURRENT START DATE'
           WHERE ROWID = rec_stg.rid;
          CONTINUE;
        END IF;

        v_prac_curr_start_date := TO_DATE(rec_stg.current_start_date,'MM/DD/YYYY');

        SELECT c.id, c.start_date, pc.id, pc.prac_id
          INTO v_prac_loc_cycle_id, v_prac_start_date,
               v_prac_net_cycle_id, v_prac_id
          FROM v_uda_prac_net_loc_additional v
          JOIN pp_prac_net_loc_cycle c ON c.id = v.prac_net_loc_cycle_id
          JOIN pp_prac_net_cycle pc ON pc.id = c.prac_net_cycle_id
         WHERE v.portico_id_uda = rec_stg.portico_id
           AND pc.net_id = rec_stg.net_id
           AND c.loc_id = rec_stg.loc_id
           AND c.start_date = v_prac_curr_start_date;

        IF v_new_end_date <= v_prac_start_date THEN
          UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
             SET status = 'FAILED: END DATE <= START DATE'
           WHERE ROWID = rec_stg.rid;
          CONTINUE;
        END IF;

        SELECT MIN(start_date)
          INTO v_prac_next_start_date
          FROM pp_prac_net_loc_cycle
         WHERE prac_net_cycle_id = v_prac_net_cycle_id
           AND loc_id = rec_stg.loc_id
           AND start_date > v_prac_start_date;

        IF v_prac_next_start_date IS NOT NULL
           AND v_new_end_date >= v_prac_next_start_date THEN
          UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
             SET status = 'FAILED: END DATE OVERLAPS NEXT CYCLE'
           WHERE ROWID = rec_stg.rid;
          CONTINUE;
        END IF;

        UPDATE pp_prac_net_loc_cycle
           SET end_date = v_new_end_date
         WHERE id = v_prac_loc_cycle_id;

        SELECT MAX(c.end_date)
          INTO v_prac_greatest_end_dt
          FROM pp_prac_net_loc_cycle c
          JOIN pp_prac_net_cycle pc ON pc.id = c.prac_net_cycle_id
         WHERE pc.prac_id = v_prac_id
           AND pc.net_id = rec_stg.net_id;

        BEGIN
          SELECT c.id, c.end_date
            INTO v_prac_solo_id, v_prac_solo_end_date
            FROM pp_prac_net_loc_cycle c
            JOIN v_uda_prac_net_loc_additional v
              ON v.prac_net_loc_cycle_id = c.id
            JOIN pp_prac_net_cycle pc
              ON pc.id = c.prac_net_cycle_id
           WHERE pc.prac_id = v_prac_id
             AND pc.net_id = rec_stg.net_id
             AND SUBSTR(v.portico_id_uda,10,3)='001';

          IF v_prac_solo_end_date <> v_prac_greatest_end_dt THEN
            UPDATE pp_prac_net_loc_cycle
               SET end_date = v_prac_greatest_end_dt
             WHERE id = v_prac_solo_id;

            UPDATE pp_prac_net_cycle
               SET end_date = v_prac_greatest_end_dt
             WHERE prac_id = v_prac_id
               AND net_id = rec_stg.net_id;
          END IF;
        EXCEPTION WHEN NO_DATA_FOUND THEN NULL;
        END;

      END IF;

      ----------------------------------------------------------
      -- SUCCESS
      ----------------------------------------------------------
      UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
         SET status = 'SUCCESS'
       WHERE ROWID = rec_stg.rid;

    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
           SET status = 'FAILED: NO MATCHING CYCLE'
         WHERE ROWID = rec_stg.rid;

      WHEN OTHERS THEN
        v_err_msg := SUBSTR(SQLERRM,1,3900);
        UPDATE BCBSMI_BL_UPDATE_TERM_NWK_END_DATE
           SET status = 'FAILED: '||v_err_msg
         WHERE ROWID = rec_stg.rid;
    END;
  END LOOP;

  COMMIT;
END sp_bcbsmi_update_term_nwk_end_dt_all;
/
..
