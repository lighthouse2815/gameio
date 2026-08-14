import type { MessageCatalog } from "@/lib/i18n/types";

export const pageVietnameseMessages = {
  "Play Network": "Mạng trò chơi",
  "Game index": "Danh mục trò chơi",
  "Game Index": "Danh mục trò chơi",
  "Create player": "Tạo người chơi",
  Multiplayer: "Nhiều người chơi",
  "Playable operations": "Các trò chơi hiện có",
  "Search the live backend catalog. Every title declares its player mode, classification, player capacity, and implementation route.":
    "Tìm trong danh mục trực tiếp từ máy chủ. Mỗi trò chơi đều nêu rõ chế độ, thể loại, số người chơi và cách vận hành.",
  Registry: "Danh mục",
  "Local engines": "Trò chơi chạy cục bộ",
  "6 installed engines": "6 BỘ MÁY ĐÃ CÀI",
  "AUTH CHANNEL / 01": "KÊNH XÁC THỰC / 01",
  Resume: "Tiếp tục",
  "Play.": "chơi.",
  "Restore your progress, verified scores, rooms, and player network. Access tokens stay only in runtime memory; refresh is handled by the secure HttpOnly server cookie channel.":
    "Khôi phục tiến trình, điểm số đã xác thực, phòng chơi và mạng lưới bạn bè. Mã truy cập chỉ tồn tại trong bộ nhớ khi chạy; việc làm mới được xử lý qua cookie HttpOnly an toàn của máy chủ.",
  "[ PLAYER IDENTIFICATION ]": "[ NHẬN DIỆN NGƯỜI CHƠI ]",
  "IDENTITY REGISTRY / 02": "ĐĂNG KÝ DANH TÍNH / 02",
  Join: "Tham gia",
  "The Grid.": "mạng lưới.",
  "One identity links your games, achievements, match history, and verified global rank.":
    "Một tài khoản kết nối trò chơi, thành tích, lịch sử trận đấu và thứ hạng toàn cầu đã xác thực của bạn.",
  "[ NEW PLAYER RECORD ]": "[ HỒ SƠ NGƯỜI CHƠI MỚI ]",
  Register: "Đăng ký",
  "Player network": "Mạng lưới người chơi",
  "Manage accepted player links and incoming requests. Presence and current-game signals are read from the realtime backend when that module is available.":
    "Quản lý bạn bè đã kết nối và các lời mời đang chờ. Trạng thái trực tuyến và trò chơi hiện tại được đọc từ máy chủ thời gian thực khi mô-đun này hoạt động.",
  "Verified telemetry": "Dữ liệu đã xác thực",
  "Global Rank": "Xếp hạng toàn cầu",
  "A read-only view of results validated and recorded by the authoritative game server. Filter the field by operation or inspect the global player index.":
    "Xem kết quả đã được máy chủ trò chơi xác thực và ghi nhận. Bạn có thể lọc theo trò chơi hoặc xem bảng xếp hạng người chơi toàn cầu.",
  "Room operations": "Vận hành phòng chơi",
  "Create or join a backend room, enter matchmaking, and wait for authoritative GAME_START state. No simulated rooms are inserted when the realtime module is offline.":
    "Tạo hoặc tham gia phòng trên máy chủ, vào hàng ghép trận và chờ trạng thái GAME_START chính thức. Hệ thống không tạo phòng giả khi mô-đun thời gian thực ngừng hoạt động.",
  "Local and account controls": "Điều khiển cục bộ và tài khoản",
  "Adjust the visual substrate, update the avatar field supported by the backend, or close the secure browser session.":
    "Điều chỉnh giao diện, cập nhật ảnh đại diện được máy chủ hỗ trợ hoặc đóng phiên trình duyệt an toàn.",
  "[ INDEXING NETWORK ]": "[ ĐANG LẬP CHỈ MỤC MẠNG ]",
  Daily: "Mỗi ngày",
  "Daily Challenge": "Thử thách hằng ngày",
  "Shared seed operation": "Thử thách cùng seed",
  "One verified solo operation every day. Everyone receives the same server seed and competes on a ranking that resets at midnight in Vietnam.":
    "Mỗi ngày có một thử thách solo được xác thực. Mọi người nhận cùng seed từ máy chủ và tranh hạng trên bảng được đặt lại lúc nửa đêm theo giờ Việt Nam.",
  "Season operations": "Vận hành mùa giải",
  "Competitive arena": "Đấu trường cạnh tranh",
  "Track game-specific seasonal Elo, inspect the live ladder, and run server-authoritative single-elimination tournaments.":
    "Theo dõi Elo mùa giải riêng cho từng game, xem bảng hạng trực tiếp và tổ chức giải loại trực tiếp do máy chủ điều khiển.",
} satisfies MessageCatalog;
