# Ý tưởng cốt lõi của IntelligentRouteX

## 1. Tuyên bố mục tiêu hiện tại

IntelligentRouteX không còn đi theo câu hỏi "có nên ghép đơn hay không". Mục tiêu hiện tại là:

> mặc định tìm cách ghép đơn một cách thông minh để tối ưu chi phí toàn hệ giao nhận, nhưng chỉ ghép khi tổng utility của khách hàng, tài xế và marketplace tốt hơn so với giao tách lẻ.

Nói rõ hơn, hệ thống phải cố gắng đạt đồng thời các mục tiêu sau:

- tài xế giao được nhiều đơn hơn trên cùng một quãng di chuyển
- khách đi cùng route với các đơn khác thì phí ship có cơ hội giảm
- tránh bắt tài xế chạy xa để đi lấy hàng
- giảm xe chạy rỗng trước pickup và sau drop
- sau khi giao xong, tài xế nên rơi vào vùng có xác suất có đơn tiếp cao
- route được chọn không chỉ nhanh nhất, mà phải tiện nhất và kinh tế nhất cho toàn hệ

## 2. Bài toán thật hệ thống đang giải

Đây không phải bài toán "chọn tài xế gần nhất". Bài toán thật là:

- trong một thành phố luôn thay đổi theo thời gian thực
- với đơn hàng đến liên tục
- với tài xế phân bố không đều
- với traffic, thời tiết, shortage và demand spike

hệ thống phải chọn route nào làm cho chi phí tổng thấp hơn trong khi vẫn giữ trải nghiệm đủ tốt cho khách và tài xế.

Chi phí tổng ở đây gồm:

- deadhead, tức quãng đường xe chạy rỗng
- thời gian vòng pickup quá xa
- rủi ro giao trễ
- rủi ro ghép đơn kém chất lượng
- rủi ro tài xế giao xong rơi vào vùng "trống đơn"

## 3. Vì sao batching là trung tâm

Batching, tức ghép nhiều đơn vào cùng một hành trình, là lõi của hệ thống vì nó tạo ra lợi ích đồng thời cho nhiều phía nếu làm đúng:

- marketplace giảm chi phí giao trên mỗi đơn
- tài xế giao được nhiều đơn hơn trên cùng thời gian làm việc
- khách có thể được chia sẻ chi phí ship nếu đi cùng route hợp lý

Nhưng batching không được làm theo kiểu "ghép càng nhiều càng tốt". Một batch chỉ được coi là tốt nếu:

- không làm pickup vòng quá xa
- không làm khách giao quá trễ
- không làm route bị rối và khó thực thi
- không khiến tài xế giao xong rơi vào vị trí xấu

Vì vậy, tư tưởng đúng của hệ thống là:

> smart batching first, not batch count first.

Nghĩa là ưu tiên ghép đơn, nhưng chỉ khi batch thực sự có lời cho toàn hệ.

## 4. Objective function ở mức business

Ở mức business, hệ thống đang tối ưu một utility tổng hợp, không tối ưu cho một bên duy nhất.

Utility đó ưu tiên theo thứ tự:

1. giữ route thực thi được và không phá guardrail SLA
2. giảm deadhead
3. tăng số đơn hoàn thành trên mỗi tài xế
4. tăng xác suất tài xế có đơn tiếp sau khi giao xong
5. giảm chi phí trung bình trên mỗi đơn

Điều này có nghĩa là hệ thống không tìm "route ngắn nhất" theo nghĩa hình học thuần túy. Nó tìm route "đáng chọn nhất" theo nghĩa vận hành:

- ít rỗng hơn
- nhiều giá trị hơn
- tiện hơn cho tài xế
- rẻ hơn cho toàn hệ

## 5. Vì sao "tiện nhất" quan trọng hơn "nhanh nhất"

Trong delivery thật, route nhanh nhất chưa chắc là route tốt nhất.

Ví dụ:

- một route pickup gần hơn nhưng khiến tài xế rơi vào vùng trống đơn sau khi giao xong có thể tệ hơn
- một route hơi lâu hơn một chút nhưng gom được thêm đơn cùng hướng có thể tốt hơn
- một route rất nhanh cho đơn hiện tại nhưng tạo deadhead lớn cho 10 phút sau có thể là quyết định xấu

Vì vậy hệ thống phải tối ưu cho:

- sự tiện của chuỗi route
- khả năng nối tiếp đơn
- chi phí rỗng toàn vòng đời route

chứ không chỉ tối ưu ETA cục bộ của một đơn lẻ.

## 6. Hệ thống tối ưu cho ai

Hệ thống tối ưu cho toàn hệ delivery, tức là cân bằng ba phía:

- khách hàng
- tài xế
- marketplace

Tuy nhiên, cân bằng không có nghĩa là thỏa hiệp mơ hồ. Hệ thống phải giữ các guardrail rõ ràng:

- không bắt tài xế chạy pickup quá xa chỉ để ghép đơn cho đẹp
- không làm khách trễ quá mức chỉ để giảm deadhead
- không ép route phức tạp quá mức khiến tài xế khó thực thi

## 7. Vì sao phải là AI-driven dispatch

Route của hệ thống không thể chỉ dựa vào rule đơn giản như nearest-driver hoặc shortest-path.

Lý do là bài toán thật cần nhìn trước hậu quả của quyết định hiện tại:

- route này có giúp giảm deadhead không
- batch này có thực sự lời hơn solo không
- sau drop, tài xế có khả năng nhận đơn tiếp ở đâu
- dưới heavy rain hoặc shortage thì nên cứu như thế nào

Vì vậy lõi route phải là AI-driven dispatch:

- model dự báo giá trị route
- model đánh giá giá trị batch
- model continuation để ước lượng cơ hội sau drop
- model stress rescue cho regime xấu
- model positioning để đưa tài xế về vùng có cơ hội đơn tiếp
- graph foresight để nhìn trước trạng thái mạng lưới thay vì chỉ nhìn khoảng cách cục bộ

## 8. Điều hệ thống không theo đuổi

Hệ thống hiện không theo đuổi các hướng sau như mục tiêu chính:

- ghép đơn bằng mọi giá
- route nhanh nhất bằng mọi giá
- dùng LLM hoặc agent để quyết định route live
- tune benchmark theo một bộ scenario tự dựng rồi tự thắng

## 9. Câu mô tả một dòng

IntelligentRouteX là hệ thống AI-first dispatch cho food delivery, ưu tiên ghép đơn thông minh để giảm chi phí toàn hệ, giảm xe rỗng, và đưa tài xế tới vị trí có cơ hội có đơn tiếp cao, trong khi vẫn giữ route tiện và thực thi được.
