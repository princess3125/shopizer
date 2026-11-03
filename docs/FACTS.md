# Sự thật đã xác minh (Shopizer 3.2.5, chạy local, MySQL 8)
- POST /api/v1/private/login {"username","password"} -> 200 {"id","token"}.
  Sai mật khẩu, sai tài khoản, body rỗng -> 401 {"message":"Bad credentials"} (content-type text/plain).
- API private không token -> 401 (JSON chuẩn Spring). Token admin bị từ chối (401) ở /api/v1/auth/customer/*.
- GET /api/v1/category và /api/v1/products -> 200 JSON {totalPages, number, recordsTotal, recordsFiltered, categories[] | products[]}; hiện đang rỗng.
- POST /api/v1/private/category -> 201, trả "id" số; DELETE /api/v1/private/category/{id} -> 200.
- POST /api/v1/private/product: thiếu tồn kho -> 400 "product must have at least one availability".
  Body đầy đủ có visible=true, available=true, dateAvailable, productShipeable=false, categories[] và khối inventory{sku, quantity, price{price, defaultPrice}} -> 201 {"id": <number>}.
  DELETE /api/v1/private/product/{id} -> 200.
- POST /api/v1/cart body {"product": <sku>, "quantity": n}:
  - SKU hợp lệ, SL n > 0 -> 201 {"code": <hash>, "total": ..., "quantity": ..., "products": [...]}.
    GET /api/v1/cart/{code} -> 200 trả về giỏ hàng tương ứng.
  - SKU không tồn tại -> 400 {"errorCode":"500","message":"Cannot get product with sku [...]"}.
  - Điểm quan sát P-08 (mức độ chưa xác minh): giỏ hàng chấp nhận SL = 0 và SL < 0 (201, total = 0.00 hoặc âm); CHƯA XÁC MINH checkout có từ chối hay không.
  - Checkout là PersistableAnonymousOrder. CHƯA XÁC MINH.
- Tổng 309 endpoint; 68 GET không tham số đường dẫn đã được quét.
- Lỗi đã biết (chưa xác minh nguyên nhân): GET content/boxes, content/pages (public và private) -> 500;
  GET sec/private/groups, sec/private/permissions -> 200 khi không có token; GET auth/refresh không token -> 500.
- Danh mục chi tiết các lỗi đã biết (5xx và không từ chối khi thiếu token): xem tệp api-tests/src/test/resources/known-issues.csv.
