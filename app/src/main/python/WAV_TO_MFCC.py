import librosa
import math
import numpy as np

def wav_to_mfcc(wav_path, n_mfcc=13, n_fft=2048, hop_length=512):
    SAMPLE_RATE = 48000
    DA_FACTOR = 10  # data augmentation factor
    X = []
    signal, sr = librosa.load(wav_path, sr=None)

    samples_per_track = len(signal)
    num_samples_per_segment = 3 * sr
    expected_num_mfcc_per_segment = math.ceil(num_samples_per_segment / hop_length)
    num_segments = int(samples_per_track / num_samples_per_segment)
    for ii in range(DA_FACTOR):
        bias = int(ii * (SAMPLE_RATE / DA_FACTOR))
        for s in range(num_segments):
            start_sample = num_samples_per_segment * s + bias
            finish_sample = min(start_sample + num_samples_per_segment, samples_per_track)

            mfcc = librosa.feature.mfcc(signal[start_sample:finish_sample],
                                        sr=sr,
                                        n_fft=n_fft,
                                        n_mfcc=n_mfcc,
                                        hop_length=hop_length,
                                        )

            mfcc = mfcc.T

            if len(mfcc) == expected_num_mfcc_per_segment:
                X.append(mfcc.tolist())

    # X is the tensor to be passed into the NN
    # add new dimension, pad, cast to tf.float32
    X = np.array(X)
    X = X[..., np.newaxis]
    max_value = max(np.max(X), -np.min(X))
    X /= max_value

    # when sample rate is 48k
    # shape of X should be (batch_size, 282, 32, 1)
    return X