import tensorflow as tf
import numpy as np
import os

# Configs
IMG_HEIGHT = 128
IMG_WIDTH = 128
NUM_SUITS = 5 # Diamond, Club, Heart, Spade, Joker
NUM_RANKS = 15 # 3-10, J, Q, K, A, 2, Black Joker, Red Joker

def create_model():
    inputs = tf.keras.Input(shape=(IMG_HEIGHT, IMG_WIDTH, 3))
    
    # Base model
    x = tf.keras.layers.Conv2D(16, 3, activation='relu')(inputs)
    x = tf.keras.layers.MaxPooling2D(2)(x)
    x = tf.keras.layers.Conv2D(32, 3, activation='relu')(x)
    x = tf.keras.layers.MaxPooling2D(2)(x)
    x = tf.keras.layers.Flatten()(x)
    
    # Feature backbone
    features = tf.keras.layers.Dense(64, activation='relu')(x)
    
    # Two outputs: Suit and Rank
    suit_out = tf.keras.layers.Dense(NUM_SUITS, activation='softmax', name='suit_output')(features)
    rank_out = tf.keras.layers.Dense(NUM_RANKS, activation='softmax', name='rank_output')(features)
    
    model = tf.keras.Model(inputs=inputs, outputs=[suit_out, rank_out])
    
    model.compile(optimizer='adam',
                  loss={'suit_output': 'sparse_categorical_crossentropy', 
                        'rank_output': 'sparse_categorical_crossentropy'},
                  metrics=['accuracy'])
    return model

def generate_dummy_data(samples=100):
    X = np.random.rand(samples, IMG_HEIGHT, IMG_WIDTH, 3).astype('float32')
    y_suit = np.random.randint(0, NUM_SUITS, samples).astype('int32')
    y_rank = np.random.randint(0, NUM_RANKS, samples).astype('int32')
    return X, {'suit_output': y_suit, 'rank_output': y_rank}

def main():
    print("Initializing model...")
    model = create_model()
    
    print("Generating synthetic dataset (for demonstration)...")
    X_train, y_train = generate_dummy_data(500)
    
    print("Training the model...")
    model.fit(X_train, y_train, epochs=2, batch_size=32)
    
    print("Converting model to LiteRT (TFLite)...")
    # Convert the model
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()
    
    # Save the model
    output_dir = "../androidApp/src/main/assets"
    os.makedirs(output_dir, exist_ok=True)
    tflite_path = os.path.join(output_dir, "card_model.tflite")
    
    with open(tflite_path, "wb") as f:
        f.write(tflite_model)
        
    print(f"Model successfully saved to {tflite_path}")

if __name__ == '__main__':
    main()
