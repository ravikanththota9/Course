#!/bin/bash

# =========================
# Input Parameters
# =========================
TEMPDIR=$1
DESTDIR=$2/$3
ARCHIVEDIR=$2/archive

# =========================
# Config
# =========================
qscore_dir="/app/eddi/PODS/PODSDEV419C/inbound/Qscore"
qscore_file_name="BCBSMI_PROVQLTYSCORE_*.csv"

alphaprefix_dir="/app/eddi/pods/inbound/clientalphaprefix"
alphaprefix_file_name="BCBSMI_ALPHAPREFIX*"

hsq_zip_file=BCBSMI_PSEARCH
hsq_qscore_file_name="BCBSMI_PROVQLTYSCORE_OOS_"
hsq_alphaprefix_file_name="BCBSMI_ALPHAPREFIX_"
hsq_ctl_file_prefix="BCBSMI_BATCH_"

# =========================
# Get Latest QScore File (Mandatory)
# =========================
qscore_file=$(find "$qscore_dir" -type f -name "$qscore_file_name" | sort -V | tail -n 1)

if [ -z "$qscore_file" ]; then
   echo "ERROR: No QScore file found"
   exit 1
fi

# =========================
# Get Latest AlphaPrefix File (Optional)
# =========================
alphaprefix_file=$(find "$alphaprefix_dir" -type f -name "$alphaprefix_file_name" | sort -V | tail -n 1)

if [ -z "$alphaprefix_file" ]; then
   echo "INFO: No AlphaPrefix file found. Skipping AlphaPrefix processing."
   PROCESS_ALPHA=0
else
   PROCESS_ALPHA=1
fi

# =========================
# Prepare Temp Dir
# =========================
cd "$TEMPDIR" || exit 1

FILESTR=""
SP=" "

shift
shift
shift

while test ${#} -gt 0
do
  FILESTR=$FILESTR$SP$1
  shift
done

# =========================
# Run Python Filter
# =========================
echo "Running filterHsqFiles.py ..."
python /app/scripts/PODS/PODSDEV219C/filterHsqFiles.py

# =========================
# Zip HSQ Files
# =========================
echo "Zipping HSQ files..."
zip $FILESTR

# =========================
# Latest HSQ ZIP
# =========================
hsq_file=$(find "$TEMPDIR" -type f -name "$hsq_zip_file*.zip" | sort -V | tail -n 1)

if [ -z "$hsq_file" ]; then
   echo "ERROR: HSQ zip not created"
   exit 1
fi

# =========================
# Extract Timestamp
# =========================
timestamp=$(basename "$hsq_file" | grep -oE '[0-9]{8}_[0-9]{6}')

if [ -z "$timestamp" ]; then
   echo "ERROR: Could not extract timestamp from zip file"
   exit 1
fi

# =========================
# QScore → TXT
# =========================
hsq_qscore_txt_file="$TEMPDIR/${hsq_qscore_file_name}${timestamp}.txt"

awk 'NR>1 {print $1 "|" $2}' FS=',' OFS='|' "$qscore_file" > "$hsq_qscore_txt_file"

zip -j "$hsq_file" "$hsq_qscore_txt_file"

hsq_qscore_count=$(wc -l < "$hsq_qscore_txt_file")

# =========================
# AlphaPrefix → TXT (Optional)
# =========================
if [ "$PROCESS_ALPHA" -eq 1 ]; then
   hsq_alphaprefix_txt_file="$TEMPDIR/${hsq_alphaprefix_file_name}${timestamp}.txt"

   cp "$alphaprefix_file" "$hsq_alphaprefix_txt_file"

   zip -j "$hsq_file" "$hsq_alphaprefix_txt_file"

   hsq_alphaprefix_count=$(wc -l < "$hsq_alphaprefix_txt_file")
fi

# =========================
# Control File Update
# =========================
hsq_ctl_file=$(find "$TEMPDIR" -type f -name "$hsq_ctl_file_prefix*.ctl" | sort -V | tail -n 1)

if [ -z "$hsq_ctl_file" ]; then
   echo "ERROR: Control file not found"
   exit 1
fi

version=$(awk -F'|' 'NR==1 { print $NF }' "$hsq_ctl_file")

echo "$(basename "$hsq_qscore_txt_file")|$hsq_qscore_count|$version" >> "$hsq_ctl_file"

if [ "$PROCESS_ALPHA" -eq 1 ]; then
   echo "$(basename "$hsq_alphaprefix_txt_file")|$hsq_alphaprefix_count|$version" >> "$hsq_ctl_file"
fi

# =========================
# Archive Old Files
# =========================
echo "Archiving old files..."
mv "$DESTDIR"/* "$ARCHIVEDIR"/ 2>/dev/null

# =========================
# Move New Files to Dest
# =========================
echo "Moving new files to destination..."
mv "$TEMPDIR"/*.zip "$DESTDIR"/
mv "$TEMPDIR"/*.ctl "$DESTDIR"/
mv "$TEMPDIR"/*.flg "$DESTDIR"/ 2>/dev/null

echo "Process completed successfully."
