*** 현 기능 사항 ***

1. 보안 로그인
  - 카카오, 파이어베이스 연동: 사용자에게 친숙한 카카오 로그인을 사용하면서, 데이터 관리는 Firebase Authentication으로 안전하게 처리
  - Google Cloud Functions(TypeScript)를 직접 구축, 카카오 토큰을 검증하고 Firebase 커스텀 토큰을 발급하는 이중 보안 로직 구현(Serverless Backend)

2. 실시간 그룹 시스템
  - 6자리 랜덤 초대 코드 생성, 고유한 방 생성
  - 초대 코드를 입력하면 만든 방에 즉시 입장
  - Firestore Snapshot Listener를 활용, 리더가 목적지나 시간을 바꾸면 모든 멤버의 화면이 새로고침 없이 실시간으로 업데이트(실시간 동기화)

3. 권한 관리 및 멤버 상태
  - 방을 만든 사람에게만 목적지 설정 및 도착 시간 설정 권한 부여
  - 각 멤버는 자신의 출발지와 준비 상태 설정 가능, 이 정보는 실시간으로 공유(멤버 상태 관리)

4. 위치 저장
  - 기존 데이터 클래스는 장소 정보를 담을 수 없었는데, 장소 이름뿐만 아니라 위도와 경도 데이터를 저장, 중간 지점 알고리즘 계산에 활용하도록

*** Backend & Database ***
- Server: Firebase Cloud Functions (Node.js / TypeScript)
- Database: Cloud Firestore (NoSQL)
- Auth: Firebase Authentication + Kakao SDK
