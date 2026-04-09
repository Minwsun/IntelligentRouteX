# Ý tưởng cốt lõi của IntelligentRouteX

## 1. Tuyên bố mục tiêu hiện tại

IntelligentRouteX không còn đi theo câu hỏi "có nên ghép đơn hay không". Mục tiêu hiện tại là:

> mặc định tìm cách ghép đơn một cách thông minh để tối ưu chi phí toàn hệ giao nhận, nhưng chỉ giữ batch khi utility tổng của khách hàng, tài xế và marketplace tốt hơn so với giao tách lẻ.

Nói rõ hơn, hệ thống phải cố gắng đạt đồng thời các mục tiêu sau:

- tài xế giao được nhiều đơn hơn trên cùng một quãng di chuyển
- khách đi cùng route với các đơn khác thì có cơ hội được giảm phí ship
- tránh bắt tài xế chạy xa để đi lấy hàng
- giảm xe chạy rỗng trước pickup và sau drop
- sau khi giao xong, tài xế nên rơi vào vùng có xác suất có đơn tiếp cao
- route được chọn không chỉ nhanh, mà còn phải tiện và kinh tế cho toàn hệ

## 2. Bài toán thật hệ thống đang giải

Đây không phải bài toán "chọn tài xế gần nhất". Bài toán thật là:

- thành phố thay đổi liên tục theo thời gian thực
- đơn hàng đến liên tục và không đều
- tài xế phân bố lệch theo vùng và thời điểm
- traffic, thời tiết, shortage và demand spike luôn làm chất lượng route dao động

Hệ thống phải chọn route nào giúp giảm chi phí tổng trong khi vẫn giữ trải nghiệm đủ tốt cho khách và tài xế.

Chi phí tổng ở đây gồm:

- deadhead, tức quãng đường xe chạy rỗng
- quãng pickup quá xa
- rủi ro giao trễ
- rủi ro batch kém chất lượng
- rủi ro tài xế giao xong rơi vào vùng "trống đơn"

## 3. Smart batching first

Batching là trung tâm của hệ thống vì nếu làm đúng thì nó tạo lợi ích đồng thời cho cả ba phía:

- marketplace giảm chi phí giao trên mỗi đơn
- tài xế giao được nhiều đơn hơn trên cùng thời gian làm việc
- khách có thể được chia sẻ chi phí ship nếu đi cùng route hợp lý

Nhưng batching không được làm theo kiểu "ghép càng nhiều càng tốt". Một batch chỉ được coi là tốt nếu:

- không làm pickup vòng quá xa
- không làm khách bị trễ vượt guardrail
- không làm route rối và khó thực thi
- không khiến tài xế rơi vào vị trí xấu sau khi giao xong

Vì vậy, tư tưởng đúng của hệ thống là:

> smart batching first, not batch count first.

Nghĩa là mặc định tìm batch tốt nhất, nhưng chỉ giữ batch khi utility toàn hệ thật sự thắng solo hoặc local extension.

## 4. Objective function ở mức business

Ở mức business, hệ thống tối ưu một utility tổng hợp chứ không tối ưu cho một bên duy nhất.

Thứ tự ưu tiên hiện tại là:

1. giữ route thực thi được và không phá guardrail SLA
2. giảm deadhead
3. tăng completed orders trên mỗi driver-hour
4. tăng cơ hội có đơn tiếp sau drop
5. giảm chi phí giao trung bình trên mỗi đơn

Điều này có nghĩa là hệ thống không tìm route "ngắn nhất" theo nghĩa hình học. Nó tìm route "đáng chọn nhất" theo nghĩa vận hành:

- ít rỗng hơn
- nhiều giá trị hơn
- tiện hơn cho tài xế
- rẻ hơn cho toàn hệ

## 5. Vì sao "tiện nhất" quan trọng hơn "nhanh nhất"

Trong delivery thật, route nhanh nhất chưa chắc là route tốt nhất.

Ví dụ:

- một route pickup gần hơn nhưng làm tài xế rơi vào vùng trống đơn sau khi giao xong có thể là route tệ
- một route hơi lâu hơn một chút nhưng gom được thêm đơn cùng hướng có thể tốt hơn
- một route rất nhanh cho đơn hiện tại nhưng tạo deadhead lớn cho 10-15 phút sau có thể là quyết định xấu

Vì vậy hệ thống phải tối ưu cho:

- sự tiện của cả chuỗi route
- khả năng nối tiếp đơn sau drop
- chi phí rỗng của toàn vòng đời route

chứ không chỉ tối ưu ETA cục bộ của một đơn lẻ.

## 6. Hệ thống tối ưu cho ai

IntelligentRouteX tối ưu cho toàn hệ delivery, tức là cân bằng ba phía:

- khách hàng
- tài xế
- marketplace

Tuy nhiên, cân bằng không có nghĩa là thỏa hiệp mơ hồ. Hệ thống vẫn phải giữ guardrail rõ ràng:

- không bắt tài xế chạy pickup quá xa chỉ để ghép đơn cho đẹp
- không làm khách trễ quá mức chỉ để giảm deadhead
- không ép route quá phức tạp khiến tài xế khó thực thi

## 7. Vì sao phải là AI-driven dispatch

Route của hệ thống không thể chỉ dựa vào nearest-driver hoặc shortest-path.

Lý do là bài toán thật cần nhìn trước hậu quả của quyết định hiện tại:

- route này có giảm deadhead thật không
- batch này có thực sự lời hơn solo không
- sau drop, tài xế có khả năng nhận đơn tiếp ở đâu
- dưới heavy rain hoặc shortage thì nên cứu bằng cách nào

Vì vậy lõi route phải là AI-driven dispatch:

- route value model chấm utility tổng thể của plan
- batch value model chấm lợi ích thật của bundle
- continuation outcome model ước lượng cơ hội sau drop
- stress rescue model xử lý regime xấu
- driver positioning value model đưa tài xế về vùng có cơ hội đơn tiếp
- graph foresight nhìn trước trạng thái mạng lưới thay vì chỉ nhìn khoảng cách hiện tại

## 8. Điều hệ thống không theo đuổi

Hệ thống hiện không theo đuổi các hướng sau như mục tiêu chính:

- ghép đơn bằng mọi giá
- tối ưu route chỉ cho tốc độ giao nhanh nhất
- dùng LLM hoặc agent để quyết định route live
- tune benchmark theo một bộ scenario tự dựng rồi tự thắng

## 9. Câu mô tả một dòng

IntelligentRouteX là hệ thống AI-first dispatch cho food delivery, ưu tiên ghép đơn thông minh để giảm chi phí toàn hệ, giảm xe rỗng, và đưa tài xế tới vị trí có cơ hội có đơn tiếp cao, trong khi vẫn giữ route tiện, kinh tế và thực thi được.
