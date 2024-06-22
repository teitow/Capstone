import requests
import json
import time

def fetch_all_products():
    api_key = "64a6b08d2f644d79b69c"
    service_id = "I2570"
    data_type = "json"
    start_idx = 1
    batch_size = 100  # 한 번에 조회할 항목 수
    end_idx = batch_size
    all_products = []

    while True:
        url = f"http://openapi.foodsafetykorea.go.kr/api/{api_key}/{service_id}/{data_type}/{start_idx}/{end_idx}"
        response = None

        # 요청 시도 및 재시도 로직
        for _ in range(5):  # 최대 5번 재시도
            try:
                response = requests.get(url)
                if response.status_code == 200:
                    break
                else:
                    print(f"API 요청 실패: {response.status_code}")
            except Exception as e:
                print(f"요청 중 에러 발생: {e}")
            time.sleep(1)  # 1초 대기 후 재시도

        if response is None or response.status_code != 200:
            print("API 요청 반복 실패. 종료합니다.")
            break

        data = response.json()
        if "I2570" in data and "row" in data["I2570"]:
            products = data["I2570"]["row"]
            if not products:
                break
            all_products.extend(products)
            start_idx += batch_size
            end_idx += batch_size
            print(f"현재 {start_idx}번째 항목까지 조회 완료...")
        else:
            break

        time.sleep(0.5)  # 각 요청 간 0.5초 대기

    return all_products

def save_products_to_file(products, filename):
    with open(filename, 'w', encoding='utf-8') as file:
        for product in products:
            barcode = product.get("BRCD_NO", "N/A")
            product_name = product.get("PRDT_NM", "N/A")
            company = product.get("CMPNY_NM", "N/A")
            file.write(f"제품명: {product_name}, 회사명: {company}, 바코드: {barcode}\n")

if __name__ == "__main__":
    all_products = fetch_all_products()
    save_products_to_file(all_products, "all_products.txt")
    print(f"총 {len(all_products)}개의 제품 정보가 'all_products.txt' 파일에 저장되었습니다.")
