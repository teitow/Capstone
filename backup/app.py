from flask import Flask, request, jsonify
import numpy as np
import tensorflow as tf
from PIL import Image, ImageOps
import io

app = Flask(__name__)

# TensorFlow Lite 모델 로드
interpreter = tf.lite.Interpreter(model_path="yolov5.tflite")
interpreter.allocate_tensors()

# 입력 및 출력 텐서 정보 가져오기
input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

# COCO 데이터셋의 표준 클래스 레이블
class_labels = [
    "person", "bicycle", "car", "motorbike", "aeroplane", "bus", "train",
    "truck", "boat", "traffic light", "fire hydrant", "stop sign",
    "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
    "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag",
    "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite",
    "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
    "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana",
    "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza",
    "donut", "cake", "chair", "sofa", "pottedplant", "bed", "diningtable", "toilet",
    "tvmonitor", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave",
    "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors",
    "teddy bear", "hair drier", "toothbrush"
]

@app.route('/detect', methods=['POST'])
def detect_objects():
    try:
        file = request.files['image'].read()  # 파일 읽기
        image = Image.open(io.BytesIO(file))

        # 모델 입력 크기에 맞게 이미지 리사이즈
        model_input_size = input_details[0]['shape'][1:3]  # 예: [416, 416]
        image = ImageOps.fit(image, model_input_size, Image.Resampling.LANCZOS)

        # 이미지를 넘파이 배열로 변환
        input_image = np.expand_dims(np.array(image, dtype=np.float32) / 255.0, axis=0)

        # 모델 추론 실행
        interpreter.set_tensor(input_details[0]['index'], input_image)
        interpreter.invoke()

        # 결과 추출
        detection_results = interpreter.get_tensor(output_details[0]['index'])

        highest_probability = 0
        detected_object = None

        for detection in detection_results[0]:  # 감지된 객체 리스트를 가정
            confidence = detection[4]
            if confidence > 0.5:  # 확률 임계값
                class_id = np.argmax(detection[5:])
                probability = detection[5:][class_id]

                if probability > highest_probability:
                    highest_probability = probability
                    detected_object = {
                        "label": class_labels[class_id],
                        "probability": float(probability),
                        "bounding_box": detection[0:4].tolist()  # y, x, height, width
                    }

        if detected_object:
            return jsonify({'detection': detected_object})
        else:
            return jsonify({'message': 'No object detected with high confidence'})

    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
