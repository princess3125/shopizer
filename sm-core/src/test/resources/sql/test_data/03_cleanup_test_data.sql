-- ==============================================================================
-- SHOPIZER TEST DATA CLEANUP SCRIPT
-- Purpose: Thu hồi và dọn dẹp sạch sẽ toàn bộ dữ liệu kiểm thử
-- Nguyên tắc an toàn:
-- 1. Xóa theo đúng thứ tự LIFO (ngược chiều phụ thuộc khóa ngoại).
-- 2. Chỉ xóa các bản ghi thuộc phạm vi kiểm thử (ID >= 90001 hoặc mã LIKE 'TEST_%').
-- 3. Không xóa hoặc làm ảnh hưởng đến bất kỳ dữ liệu gốc nào của hệ thống.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- BƯỚC 1: XÓA DỮ LIỆU ĐƠN HÀNG (Order Sub-entities -> Orders)
-- ------------------------------------------------------------------------------

-- 1.1 Xóa lịch sử trạng thái đơn hàng
DELETE FROM ORDER_STATUS_HISTORY 
WHERE ORDER_ID BETWEEN 90001 AND 90005 
   OR ORDER_STATUS_HISTORY_ID BETWEEN 90401 AND 90410;

-- 1.2 Xóa chi tiết cấu thành tổng giá đơn hàng
DELETE FROM ORDER_TOTAL 
WHERE ORDER_ID BETWEEN 90001 AND 90005 
   OR ORDER_ACCOUNT_ID BETWEEN 90301 AND 90320;

-- 1.3 Xóa giá từng sản phẩm trong đơn hàng
DELETE FROM ORDER_PRODUCT_PRICE 
WHERE ORDER_PRODUCT_PRICE_ID BETWEEN 90201 AND 90206
   OR ORDER_PRODUCT_ID IN (SELECT ORDER_PRODUCT_ID FROM ORDER_PRODUCT WHERE ORDER_ID BETWEEN 90001 AND 90005);

-- 1.4 Xóa các mục sản phẩm trong đơn hàng
DELETE FROM ORDER_PRODUCT 
WHERE ORDER_ID BETWEEN 90001 AND 90005 
   OR ORDER_PRODUCT_ID BETWEEN 90101 AND 90106;

-- 1.5 Xóa đơn hàng chính
DELETE FROM ORDERS 
WHERE ORDER_ID BETWEEN 90001 AND 90005 
   OR CART_CODE LIKE 'TEST_CART_%'
   OR CUSTOMER_EMAIL_ADDRESS LIKE 'test_customer_%@shopizer.com';


-- ------------------------------------------------------------------------------
-- BƯỚC 2: XÓA DỮ LIỆU KHÁCH HÀNG (Customer Group -> Customer)
-- ------------------------------------------------------------------------------

-- 2.1 Xóa liên kết nhóm người dùng
DELETE FROM CUSTOMER_GROUP 
WHERE CUSTOMER_ID BETWEEN 90001 AND 90003;

-- 2.2 Xóa hồ sơ khách hàng
DELETE FROM CUSTOMER 
WHERE CUSTOMER_ID BETWEEN 90001 AND 90003 
   OR CUSTOMER_NICK LIKE 'test_cust_%'
   OR CUSTOMER_EMAIL_ADDRESS LIKE 'test_customer_%@shopizer.com';


-- ------------------------------------------------------------------------------
-- BƯỚC 3: XÓA DỮ LIỆU GIÁ, TỒN KHO VÀ SẢN PHẨM (Price -> Availability -> Product)
-- ------------------------------------------------------------------------------

-- 3.1 Xóa mô tả giá sản phẩm
DELETE FROM PRODUCT_PRICE_DESCRIPTION 
WHERE DESCRIPTION_ID BETWEEN 90501 AND 90520
   OR PRODUCT_PRICE_ID BETWEEN 90401 AND 90420;

-- 3.2 Xóa giá bán sản phẩm
DELETE FROM PRODUCT_PRICE 
WHERE PRODUCT_PRICE_ID BETWEEN 90401 AND 90420
   OR PRODUCT_AVAIL_ID BETWEEN 90301 AND 90320;

-- 3.3 Xóa tồn kho sản phẩm (Product Availability)
DELETE FROM PRODUCT_AVAILABILITY 
WHERE PRODUCT_AVAIL_ID BETWEEN 90301 AND 90320
   OR PRODUCT_ID BETWEEN 90101 AND 90120;

-- 3.4 Xóa liên kết Sản phẩm - Danh mục (Join table)
DELETE FROM PRODUCT_CATEGORY 
WHERE PRODUCT_ID BETWEEN 90101 AND 90120 
   OR CATEGORY_ID BETWEEN 90001 AND 90005;

-- 3.5 Xóa mô tả sản phẩm
DELETE FROM PRODUCT_DESCRIPTION 
WHERE DESCRIPTION_ID BETWEEN 90201 AND 90220
   OR PRODUCT_ID BETWEEN 90101 AND 90120;

-- 3.6 Xóa thông tin sản phẩm chính
DELETE FROM PRODUCT 
WHERE PRODUCT_ID BETWEEN 90101 AND 90120 
   OR SKU LIKE 'TEST_SKU_%';


-- ------------------------------------------------------------------------------
-- BƯỚC 4: XÓA DỮ LIỆU DANH MỤC (Category Description -> Subcategory -> Root Category)
-- ------------------------------------------------------------------------------

-- 4.1 Xóa mô tả danh mục
DELETE FROM CATEGORY_DESCRIPTION 
WHERE DESCRIPTION_ID BETWEEN 90001 AND 90005
   OR CATEGORY_ID BETWEEN 90001 AND 90005;

-- 4.2 Xóa danh mục con trước (tránh lỗi khóa ngoại đệ quy PARENT_ID)
DELETE FROM CATEGORY 
WHERE PARENT_ID IS NOT NULL 
  AND (CATEGORY_ID BETWEEN 90001 AND 90005 OR CODE LIKE 'TEST_CAT_%');

-- 4.3 Xóa danh mục gốc
DELETE FROM CATEGORY 
WHERE CATEGORY_ID BETWEEN 90001 AND 90005 
   OR CODE LIKE 'TEST_CAT_%';


-- ------------------------------------------------------------------------------
-- BƯỚC 5: XÁC NHẬN DỌN DẸP HOÀN TẤT
-- Kì vọng tất cả đều trả về 0
-- ------------------------------------------------------------------------------
SELECT 'CATEGORY' AS Entity, COUNT(*) AS RemainingCount FROM CATEGORY WHERE CATEGORY_ID BETWEEN 90001 AND 90005 OR CODE LIKE 'TEST_%'
UNION ALL
SELECT 'PRODUCT', COUNT(*) FROM PRODUCT WHERE PRODUCT_ID BETWEEN 90101 AND 90120 OR SKU LIKE 'TEST_%'
UNION ALL
SELECT 'CUSTOMER', COUNT(*) FROM CUSTOMER WHERE CUSTOMER_ID BETWEEN 90001 AND 90003 OR CUSTOMER_NICK LIKE 'test_%'
UNION ALL
SELECT 'ORDERS', COUNT(*) FROM ORDERS WHERE ORDER_ID BETWEEN 90001 AND 90005;
