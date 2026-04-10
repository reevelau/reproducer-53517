CREATE SEQUENCE s_user_info_id START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE user_info (
	id NUMBER DEFAULT s_user_info_id.NEXTVAL PRIMARY KEY, 
	form_addr VARCHAR2(5 BYTE), 
	last_name VARCHAR2(100 BYTE), 
	first_name VARCHAR2(100 BYTE), 
	chinese_name VARCHAR2(100 BYTE), 
	nickname VARCHAR2(100 BYTE), 
	ad_acc VARCHAR2(500 CHAR) NOT NULL, 
	post_id VARCHAR2(10 CHAR), 
	post_title VARCHAR2(100 BYTE), 
	post_title_long_en VARCHAR2(500 BYTE), 
	post_title_long_ch VARCHAR2(500 BYTE), 
	ln VARCHAR2(50 BYTE), 
	email1 VARCHAR2(100 CHAR), 
	email2 VARCHAR2(100 CHAR), 
	active VARCHAR2(5) DEFAULT 'Y' CHECK (active IN ('Y', 'N')) NOT NULL, 
	weekly_working_min INT
);

MERGE INTO USER_INFO dst
USING (
  SELECT
    0 AS ID,
    'System' AS FIRST_NAME,
    'Account' AS LAST_NAME,
    'System' AS CHINESE_NAME,
    'System' AS NICKNAME,
    'system' AS AD_ACC,
    'Y' AS ACTIVE
  FROM dual
) src
ON (dst.ID = src.ID)
WHEN MATCHED THEN
  UPDATE SET
    dst.FIRST_NAME = src.FIRST_NAME,
    dst.LAST_NAME = src.LAST_NAME,
    dst.CHINESE_NAME = src.CHINESE_NAME,
    dst.NICKNAME = src.NICKNAME,
    dst.AD_ACC = src.AD_ACC,
    dst.ACTIVE = src.ACTIVE
WHEN NOT MATCHED THEN
  INSERT (
    ID,
    FIRST_NAME,
    LAST_NAME,
    CHINESE_NAME,
    NICKNAME,
    AD_ACC,
    ACTIVE
  )
  VALUES (
    src.ID,
    src.FIRST_NAME,
    src.LAST_NAME,
    src.CHINESE_NAME,
    src.NICKNAME,
    src.AD_ACC,
    src.ACTIVE
  );
