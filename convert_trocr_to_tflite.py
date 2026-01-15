#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
TrOCR 模型转换脚本
将 PyTorch 格式的 TrOCR 模型转换为 TensorFlow Lite 格式

使用方法:
    python convert_trocr_to_tflite.py

依赖:
    pip install torch transformers tensorflow onnx tf2onnx onnx-tf

注意:
    1. 转换过程可能需要较长时间（10-30分钟）
    2. 需要较大的内存（建议 8GB+）
    3. 最终模型文件可能较大（100-300MB）
"""

import os
import sys
import torch
import numpy as np
from pathlib import Path

# 检查依赖
def check_dependencies():
    """检查必要的依赖是否已安装"""
    required_packages = ['torch', 'transformers', 'tensorflow', 'onnx']
    missing_packages = []
    
    for package in required_packages:
        try:
            __import__(package)
        except ImportError:
            missing_packages.append(package)
    
    if missing_packages:
        print(f"错误: 缺少以下依赖包: {', '.join(missing_packages)}")
        print(f"请运行: pip install {' '.join(missing_packages)}")
        sys.exit(1)

def download_trocr_model(model_name="microsoft/trocr-base-handwritten"):
    """
    下载 TrOCR 模型
    
    注意: 中文手写识别模型可能需要使用 'chineseocr/trocr-chinese' 或其他模型
    """
    print(f"正在下载 TrOCR 模型: {model_name}")
    print("这可能需要几分钟时间，请耐心等待...")
    
    try:
        from transformers import TrOCRProcessor, VisionEncoderDecoderModel
        
        processor = TrOCRProcessor.from_pretrained(model_name)
        model = VisionEncoderDecoderModel.from_pretrained(model_name)
        
        print("模型下载完成!")
        return model, processor
    except Exception as e:
        print(f"下载模型失败: {e}")
        print("\n提示:")
        print("1. 检查网络连接")
        print("2. 如果使用中文模型，可能需要从 GitHub 下载:")
        print("   https://github.com/chineseocr/trocr-chinese")
        sys.exit(1)

def convert_to_onnx(model, processor, output_path="trocr_model.onnx"):
    """
    将 PyTorch 模型转换为 ONNX 格式
    
    注意: TrOCR 是序列到序列模型，转换较为复杂
    """
    print("\n正在转换为 ONNX 格式...")
    print("警告: TrOCR 的转换过程可能不完美，因为它是 seq2seq 模型")
    
    try:
        model.eval()
        
        # TrOCR 的输入格式: (batch_size, 3, height, width)
        # 通常输入尺寸为 384x384 或 224x224
        dummy_input = torch.randn(1, 3, 384, 384)
        
        # 导出为 ONNX
        torch.onnx.export(
            model,
            dummy_input,
            output_path,
            input_names=['pixel_values'],
            output_names=['logits'],
            dynamic_axes={
                'pixel_values': {0: 'batch_size'},
                'logits': {0: 'batch_size', 1: 'sequence_length'}
            },
            opset_version=14,  # 使用较高的 opset 版本以支持更多操作
            do_constant_folding=True
        )
        
        print(f"ONNX 模型已保存: {output_path}")
        return output_path
    except Exception as e:
        print(f"转换为 ONNX 失败: {e}")
        print("\n可能的解决方案:")
        print("1. TrOCR 模型结构复杂，直接转换可能不支持")
        print("2. 考虑使用其他方法，如 PyTorch Mobile 或 ONNX Runtime")
        print("3. 或者查找已经转换好的模型")
        raise

def convert_onnx_to_tflite(onnx_path, output_path="trocr_model.tflite"):
    """
    将 ONNX 模型转换为 TensorFlow Lite 格式
    """
    print("\n正在转换为 TensorFlow Lite 格式...")
    
    try:
        import tensorflow as tf
        
        # 方法1: 使用 tf2onnx 转换（如果可用）
        try:
            import tf2onnx
            print("使用 tf2onnx 转换...")
            # tf2onnx 主要用于反向转换，这里我们需要其他方法
        except ImportError:
            pass
        
        # 方法2: 使用 onnx-tf (已弃用，但可能仍然可用)
        try:
            import onnx_tf
            print("使用 onnx-tf 转换...")
            
            # 加载 ONNX 模型
            import onnx
            onnx_model = onnx.load(onnx_path)
            
            # 转换为 TensorFlow
            tf_rep = onnx_tf.backend.prepare(onnx_model)
            
            # 保存为 SavedModel
            tf_rep.export_graph("trocr_tf_model")
            
            # 转换为 TFLite
            converter = tf.lite.TFLiteConverter.from_saved_model("trocr_tf_model")
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            
            tflite_model = converter.convert()
            
            with open(output_path, 'wb') as f:
                f.write(tflite_model)
            
            print(f"TensorFlow Lite 模型已保存: {output_path}")
            return output_path
        except ImportError:
            print("onnx-tf 未安装，尝试其他方法...")
            print("请运行: pip install onnx-tf")
        except Exception as e:
            print(f"使用 onnx-tf 转换失败: {e}")
        
        # 如果以上方法都失败，提供建议
        print("\n自动转换失败。建议:")
        print("1. 手动使用 onnx-tf 或 tf2onnx 转换")
        print("2. 或者考虑使用 ONNX Runtime Mobile 替代 TensorFlow Lite")
        print("3. 或者查找已经转换好的模型文件")
        
        return None
        
    except Exception as e:
        print(f"转换为 TensorFlow Lite 失败: {e}")
        return None

def main():
    """主函数"""
    print("=" * 60)
    print("TrOCR 模型转换脚本")
    print("=" * 60)
    
    # 检查依赖
    check_dependencies()
    
    # 模型名称选择
    # 中文模型选项：
    # 1. microsoft/trocr-base-handwritten-zh (微软官方中文手写模型，推荐)
    # 2. chineseocr/trocr-chinese (社区中文模型)
    # 英文模型：
    # 3. microsoft/trocr-base-handwritten (英文手写模型)
    
    print("\n请选择模型:")
    print("1. microsoft/trocr-base-handwritten (英文手写，可用)")
    print("2. microsoft/trocr-base-printed (英文印刷体)")
    print("3. microsoft/trocr-small-handwritten (小型英文手写)")
    print("\n⚠️  注意: 中文 TrOCR 模型可能需要从 GitHub 手动下载")
    print("   推荐: 继续使用 PaddleOCR（当前方案已经可用）")
    
    choice = input("\n请选择 (1/2/3，默认1): ").strip()
    if choice == "2":
        model_name = "microsoft/trocr-base-printed"
    elif choice == "3":
        model_name = "microsoft/trocr-small-handwritten"
    else:
        model_name = "microsoft/trocr-base-handwritten"  # 默认使用英文手写模型
    
    print(f"\n使用的模型: {model_name}")
    print("⚠️  这是英文识别模型（中文识别效果可能不佳）")
    print("💡 提示: 如需中文识别，建议继续使用 PaddleOCR")
    
    response = input("\n是否继续? (y/n): ")
    if response.lower() != 'y':
        print("已取消")
        return
    
    try:
        # 下载模型
        model, processor = download_trocr_model(model_name)
        
        # 转换为 ONNX
        onnx_path = convert_to_onnx(model, processor)
        
        # 转换为 TFLite
        tflite_path = convert_onnx_to_tflite(onnx_path)
        
        if tflite_path and os.path.exists(tflite_path):
            file_size = os.path.getsize(tflite_path) / (1024 * 1024)  # MB
            print(f"\n{'=' * 60}")
            print("转换完成!")
            print(f"模型文件: {tflite_path}")
            print(f"文件大小: {file_size:.2f} MB")
            print("\n下一步:")
            print(f"1. 将 {tflite_path} 复制到:")
            print("   app/src/main/assets/trocr/model.tflite")
            print("2. 重新编译应用")
        else:
            print("\n转换未完成，请参考错误信息")
            
    except KeyboardInterrupt:
        print("\n\n用户中断")
    except Exception as e:
        print(f"\n转换过程出错: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()

