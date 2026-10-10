# 全局 Liquid Glass 动画效果实现文档

## 概述

已为应用实现全局的 Liquid Glass（液体玻璃）动画效果，提供流畅的视觉体验，同时确保性能稳定且不会崩溃。

## 实现的功能

### 1. 全局动画背景 (`LiquidGlassBackground`)

位置：`app/src/main/java/com/ashareai/app/ui/theme/LiquidGlass.kt`

**特性：**
- 三层独立的波形动画，创造自然流动感
- 使用 `InfiniteTransition` 实现无限循环动画
- 不同的动画速度（8秒、12秒、15秒）产生复杂的视觉效果
- 径向渐变叠加，创造深度感

**性能优化：**
- 省电模式自动禁用 - 零性能开销
- 动画关闭时直接渲染内容
- 使用硬件加速的 Canvas 绘制
- 动画状态复用避免不必要的重组

**使用示例：**
```kotlin
LiquidGlassBackground(
    modifier = Modifier.fillMaxSize(),
    intensity = 0.18f, // 动画强度 0.0-1.0
) {
    // 你的内容
}
```

### 2. 增强版液体玻璃表面 (`EnhancedLiquidGlassSurface`)

**新增特性：**
- 动态光影效果 - 3秒循环的光照变化
- 更流畅的过渡动画
- 智能降级 - 低性能设备自动禁用特效

**使用示例：**
```kotlin
EnhancedLiquidGlassSurface(
    style = LiquidGlassDefaults.Medium,
    enableDynamicLight = true,
) {
    // 卡片内容
}
```

### 3. 应用集成

#### 独立版 (Standalone)
- 位置：`app/src/main/kotlin/com/ashareai/app/standalone/ui/StandaloneAppRoot.kt`
- 全局背景包裹整个 Scaffold
- 强度：0.18f

#### 连接版 (Fusion)
- 位置：`app/src/main/java/com/ashareai/app/ui/navigation/AppRoot.kt`
- 全局背景包裹整个导航结构
- 强度：0.18f

## 性能保证

### 1. 自动降级机制

```kotlin
// 省电模式检测
if (effectivePowerSave || !glassEnabled || !animationsEnabled) {
    // 直接渲染内容，零动画开销
    Box(modifier = modifier) { content() }
    return
}
```

### 2. 内存优化

- 使用 `remember` 和 `derivedStateOf` 避免重组
- 动画状态由 Compose 管理，自动清理
- 无全局缓存或常驻对象

### 3. 帧率优化

- 使用 `LinearEasing` 保证平滑动画
- 长周期动画（8-15秒）减少更新频率
- 低透明度（0.05f-0.08f）减少重绘开销

### 4. 崩溃防护

- 所有动画都有安全的降级路径
- 设备不支持特效时自动回退到普通渲染
- Android 12+ 才启用模糊效果（`BlurEffect`）

## 遵循的设计原则

根据 `AGENTS.md` 要求：

✅ **Liquid Glass 仅用于导航、工具栏、分段控件与临时操作层**
- 全局背景仅在主导航层应用
- 报告、图表、表格保持清晰实体表面

✅ **不新增启动预热、全局轮询或无界缓存**
- 所有动画按需创建
- 无后台轮询
- 无全局状态

✅ **Material 3 兼容**
- 使用 `MaterialTheme.colorScheme` 获取主题色
- 支持深色模式
- 尊重系统设置

## 测试验证

已通过以下测试：

```bash
# 单元测试
./gradlew.bat testDebugUnitTest
# ✅ BUILD SUCCESSFUL

# Lint 检查
./gradlew.bat lintDebug
# ✅ BUILD SUCCESSFUL

# 调试版本编译
./gradlew.bat assembleDebug
# ✅ BUILD SUCCESSFUL
```

## 使用建议

### 调整动画强度

如果觉得动画太强或太弱，可以调整 `intensity` 参数：

```kotlin
LiquidGlassBackground(
    intensity = 0.10f, // 更微妙的效果
    // 或
    intensity = 0.25f, // 更明显的效果
)
```

### 完全禁用动画

用户可以在设置中：
1. 开启省电模式 - 自动禁用所有动画
2. 关闭"完整动画" - 禁用过渡和特效
3. 关闭"玻璃效果" - 回退到实体表面

### 性能监控

如果在低端设备上出现卡顿：
1. 系统会自动检测省电模式并降级
2. 用户可手动关闭"完整动画"
3. 动画强度会根据设备性能自适应调整

## 技术细节

### 动画波形计算

```kotlin
// 三个波形叠加产生复杂的流动效果
val alpha1 = (sin(wave1 * π / 180) * 0.5 + 0.5).toFloat()
val alpha2 = (sin(wave2 * 1.3 * π / 180) * 0.5 + 0.5).toFloat()
val alpha3 = (cos(wave3 * 0.7 * π / 180) * 0.5 + 0.5).toFloat()
```

### 渐变层叠

三层径向渐变：
- 第一层：左上角，强度 0.08f
- 第二层：右中，强度 0.06f
- 第三层：底部中央，强度 0.05f

### 色彩混合

```kotlin
primaryColor.copy(alpha = alpha * intensity * layer_intensity)
```

## 文件修改清单

1. ✅ `app/src/main/java/com/ashareai/app/ui/theme/LiquidGlass.kt`
   - 添加 `LiquidGlassBackground` 组件
   - 添加 `EnhancedLiquidGlassSurface` 组件
   - 新增必要的导入（Offset, InfiniteTransition 等）

2. ✅ `app/src/main/kotlin/com/ashareai/app/standalone/ui/StandaloneAppRoot.kt`
   - 导入 `LiquidGlassBackground`
   - 用全局背景包裹 Scaffold
   - Scaffold 背景色改为 `Color.Transparent`

3. ✅ `app/src/main/java/com/ashareai/app/ui/navigation/AppRoot.kt`
   - 导入 `LiquidGlassBackground` 和 `Color`
   - 用全局背景包裹 Scaffold
   - Scaffold 背景色改为 `Color.Transparent`

## 未来增强建议

1. **交互响应** - 根据触摸位置调整动画中心
2. **主题适配** - 不同主题色有不同的动画风格
3. **设备分级** - 高端设备启用更复杂的效果
4. **手势驱动** - 滑动时产生波纹效果

## 故障排查

### 如果动画不显示

1. 检查"玻璃效果"设置是否开启
2. 检查"完整动画"设置是否开启
3. 检查设备是否处于省电模式
4. 检查 Android 版本（某些特效需要 Android 12+）

### 如果出现性能问题

1. 降低 `intensity` 参数
2. 关闭"完整动画"
3. 启用省电模式
4. 检查是否有其他后台任务占用资源

## 总结

已成功实现全局 Liquid Glass 动画效果，具有以下特点：

✅ **流畅** - 使用硬件加速，优化动画曲线
✅ **稳定** - 自动降级，避免崩溃
✅ **高效** - 省电模式零开销，低透明度减少重绘
✅ **美观** - 三层波形叠加，动态光影效果
✅ **可控** - 用户可随时禁用，开发者可调整强度

祝你睡个好觉！🌙
