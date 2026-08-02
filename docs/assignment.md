# Assignment: Movie Crawler & Web Service

## Bài 2 — Web Crawler & SQLite

Viết chương trình đơn giản để crawl data từ một kho url. Các Url này là các nội dung của các
movie của trang https://toivote.com/. Tạo maven project với project encode là utf-8. Sử dụng thư viện
lấy nội dung html của các url trên và nội dung của bài báo hoặc thông số kỹ thuật của sản phẩm được
ghi lại. Dữ liệu kho ~100 URL movie bất kỳ và nội dung sau khi crawl về sẽ lưu ở SQLite, data được
backup trên disk.

Nội dung cần bóc tác bao gồm:
- Tiêu đề phim
- Năm sản xuất
- Đất nước
- Thể loại (danh sách)
- Đạo diễn (Danh sách nếu có)
- Diễn viên (Danh sách nếu có)

Tiếp theo build toàn bộ project thành một file jar duy nhất chứa tất cả các thư viện đi kèm (sử
dụng plug-in jar-with-dependencies của maven).

Tạo một máy ảo linux sử dụng Docker hoặc subsystem (lưu ý không cài các bài linux có giao
diện). Tiến hành cài openssh trên máy ảo, Sử dụng một tool SSP (như xmanager, PuTTY,
powershell…) trên máy thật và remote ssh vào máy ảo sử dụng ssh-key mà không sử dụng phương
pháp remote bằng password.

Copy file jar vừa build vào trong máy ảo sử dụng câu lệnh SCP. Sau đó dùng shell script của
linux (file .sh) để chạy file jar mỗi 5 giây một lần.

## Bài 3 — REST Web Service

Viết một webservice đơn giản với việc trả lại nội dung truyền vào theo url movie đã crawl được ở
bài 2. Kết quả trả lại dưới dạng json đã được format đẹp.

Hãy chạy chương trình trong chế độ debug, sử dụng đặt điều kiện vào breakpoint để dừng chương
trình tại diễn viên có tên bắt đầu băng chữ "A". Lưu ý: không thêm các câu lệnh rẽ nhánh vào code
để thực hiện debug.

## Bài 4 — Custom CacheTTL

Hãy tự design một cache riêng cho webservice ở bài 3 để giúp giảm số lần truy cập db nếu user
truy cập lấy các url giống nhau. Sử dụng cache bằng dạng Map<> để trả lại ngay kết quả cho số muốn
n nhập vào nếu số n đã tồn tại trong cache. Ngoài ra, Cache này có thêm TTL (thời gian sống cho
từng phần tử):
- sau khi ghi một phần tử mới vào Cache thì sau m giây thì sẽ tự xóa phần tử này đi
- nếu sau n giây không có request đọc phần tử nào đó trong Cache (sử dụng hàm get vào
    phần tử đó) thì Cache cũng tự xóa đi.

Viết cache này bằng cách tạo ra một Class mới là CacheTTL<K, V> implements Map<K,
V>, trong đó đảm bảo những phương thức sau:
- CacheTTL(int n, int m) : hàm khởi tạo với 2 giá trị tham số n, m
- V get(K key) : hàm trả lại value tương ứng với key
- void put(K key, V value): hàm đẩy cặp giá trị tương ứng vào cache
- Map<K,V> getMap(): hàm trả lại tất cả các thành phần còn lại trong cache
- Int getHitRate(): hàm trả lại tỷ lệ hit khi sử dụng cache.

## Bài 5 — Guava Cache & Git Conflict

Thay thế cache trên bài 4 bằng thư viện Guava cache, vẫn xóa sau 10s không có request và 20s sau
khi ghi vào cache. Upload project lên github.com, đồng thời cùng lúc làm 2 việc sau để tạo confict
code:
- Vào project trên github, mở một file trên trình duyệt, sửa nội dung file thêm dòng `/* Chinh sua tren server github */`, sau đó lưu lại
- Vào project trên máy, cũng vào file đó nhưng thêm dòng `/* Chinh sua tren server may client */`, sau đó Commit và Push lên git server

Hãy merge và edit conflict 2 đoạn code trên và sửa 2 dòng trên thành dòng comment sau: `/* Chinh sua hop nhat giua server & client */`

**NOTE:** PROJECT ĐẨY LÊN GIT KHÔNG NÊN ĐỂ PUBLIC.

## Bài 6 — Deploy, Auth & Rate Limiting

Deploy project bài 5 thông qua git clone và build file jar bằng lệnh của maven trên máy ảo (docker
hoặc subsystem). Hãy set heap size tối đa khi chạy chương trình là 512MB, và heap size khi mới khởi
tạo là 125MB.

Sửa webservice trên, yêu cầu phải có bước đăng nhập trước khi sử dụng. Sau khi đăng nhập thành
công mới có thể sử dụng api. (`http://localhost:8080/prime?n=10000` — Lưu ý: Không viết giao diện đăng
nhập). Yêu cầu mỗi user không request quá 2 lần mỗi 5s và 10 lần mỗi 1 phút.

---

**LƯU Ý:** Project của các bài tập upload lên git thì phải để public để review code
