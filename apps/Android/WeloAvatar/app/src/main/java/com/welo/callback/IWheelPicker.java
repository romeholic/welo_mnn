package com.welo.callback;

import android.graphics.drawable.Drawable;

import com.welo.widget.WheelPicker;

import java.util.List;

/**
 * 滚轮选择器方法接口（图片显示版）
 * <p>
 * Interface of Image WheelPicker
 *
 * @author Updated to image version
 * @version 2.0.0
 */
public interface IWheelPicker {

    /**
     * 获取滚轮选择器可见数据项的数量
     *
     * @return 滚轮选择器可见数据项的数量
     */
    int getVisibleItemCount();

    /**
     * 设置滚轮选择器可见数据项数量
     * 滚轮选择器的可见数据项数量必须为大于1的整数
     * 最终会被转换为奇数
     * 默认情况下滚轮选择器可见数据项数量为7
     *
     * @param count 滚轮选择器可见数据项数量
     */
    void setVisibleItemCount(int count);

    /**
     * 滚轮选择器数据项是否为循环状态
     *
     * @return 是否为循环状态
     */
    boolean isCyclic();

    /**
     * 设置滚轮选择器数据项是否为循环状态
     * 开启数据循环会使滚轮选择器上下滚动不再有边界
     *
     * @param isCyclic 是否为循环状态
     */
    void setCyclic(boolean isCyclic);

    /**
     * 设置滚轮Item选中监听器
     *
     * @param listener 滚轮Item选中监听器{@link WheelPicker.OnItemSelectedListener}
     */
    void setOnItemSelectedListener(WheelPicker.OnItemSelectedListener listener);

    /**
     * 获取当前被选中的数据项所显示的数据在数据源中的位置
     *
     * @return 当前被选中的数据项所显示的数据在数据源中的位置
     */
    int getSelectedItemPosition();

    /**
     * 设置当前被选中的数据项所显示的数据在数据源中的位置
     *
     * @param position 当前被选中的数据项所显示的数据在数据源中的位置
     */
    void setSelectedItemPosition(int position);

    /**
     * 获取当前被选中的数据项所显示的数据在数据源中的位置
     * 与{@link #getSelectedItemPosition()}不同的是，该方法所返回的结果会因为滚轮选择器的改变而改变
     *
     * @return 当前被选中的数据项所显示的数据在数据源中的位置
     */
    int getCurrentItemPosition();

    /**
     * 获取图片数据源
     *
     * @return 图片数据列表
     */
    List<Drawable> getImageData();

    /**
     * 设置图片数据源
     *
     * @param data 图片数据列表
     */
    void setImageData(List<Drawable> data);

    /**
     * 设置数据项是否有相同的宽度
     *
     * @param hasSameSize 是否有相同的宽度
     */
    void setSameWidth(boolean hasSameSize);

    /**
     * 数据项是否有相同宽度
     *
     * @return 是否有相同宽度
     */
    boolean hasSameWidth();

    /**
     * 设置滚轮滚动状态改变监听器
     *
     * @param listener 滚轮滚动状态改变监听器
     */
    void setOnWheelChangeListener(WheelPicker.OnWheelChangeListener listener);

    /**
     * 获取图片尺寸（px）
     *
     * @return 图片尺寸
     */
    int getImageSize();

    /**
     * 设置图片尺寸（px）
     *
     * @param size 图片尺寸
     */
    void setImageSize(int size);

    /**
     * 设置滚轮选择器是否显示指示器
     *
     * @param hasIndicator 是否有指示器
     */
    void setIndicator(boolean hasIndicator);

    /**
     * 滚轮选择器是否有指示器
     *
     * @return 滚轮选择器是否有指示器
     */
    boolean hasIndicator();

    /**
     * 获取滚轮选择器指示器尺寸
     *
     * @return 滚轮选择器指示器尺寸
     */
    int getIndicatorSize();

    /**
     * 设置滚轮选择器指示器尺寸
     *
     * @param size 滚轮选择器指示器尺寸，单位：px
     */
    void setIndicatorSize(int size);

    /**
     * 获取滚轮选择器指示器颜色
     *
     * @return 滚轮选择器指示器颜色，16位颜色值
     */
    int getIndicatorColor();

    /**
     * 设置滚轮选择器指示器颜色
     *
     * @param color 滚轮选择器指示器颜色，16位颜色值
     */
    void setIndicatorColor(int color);

    /**
     * 设置滚轮选择器是否显示幕布
     *
     * @param hasCurtain 滚轮选择器是否显示幕布
     */
    void setCurtain(boolean hasCurtain);

    /**
     * 滚轮选择器是否显示幕布
     *
     * @return 滚轮选择器是否显示幕布
     */
    boolean hasCurtain();

    /**
     * 获取滚轮选择器幕布颜色
     *
     * @return 滚轮选择器幕布颜色，16位颜色值
     */
    int getCurtainColor();

    /**
     * 设置滚轮选择器幕布颜色
     *
     * @param color 滚轮选择器幕布颜色，16位颜色值
     */
    void setCurtainColor(int color);

    /**
     * 设置滚轮选择器是否有空气感
     *
     * @param hasAtmospheric 滚轮选择器是否有空气感
     */
    void setAtmospheric(boolean hasAtmospheric);

    /**
     * 滚轮选择器是否有空气感
     *
     * @return 滚轮选择器是否有空气感
     */
    boolean hasAtmospheric();

    /**
     * 滚轮选择器是否开启卷曲效果
     *
     * @return 滚轮选择器是否开启卷曲效果
     */
    boolean isCurved();

    /**
     * 设置滚轮选择器是否开启卷曲效果
     *
     * @param isCurved 滚轮选择器是否开启卷曲效果
     */
    void setCurved(boolean isCurved);

    /**
     * 获取滚轮选择器数据项的对齐方式
     *
     * @return 滚轮选择器数据项的对齐方式
     */
    int getItemAlign();

    /**
     * 设置滚轮选择器数据项的对齐方式
     *
     * @param align 对齐方式标识值
     *              该值仅能是下列值之一：
     *              {@link WheelPicker#ALIGN_CENTER}
     *              {@link WheelPicker#ALIGN_LEFT}
     *              {@link WheelPicker#ALIGN_RIGHT}
     */
    void setItemAlign(int align);

    /**
     * 获取图片缩放类型
     * 0: FIT_CENTER, 1: CENTER_CROP, 2: CENTER_INSIDE
     *
     * @return 图片缩放类型
     */
    int getImageScaleType();

    /**
     * 设置图片缩放类型
     * 0: FIT_CENTER, 1: CENTER_CROP, 2: CENTER_INSIDE
     *
     * @param scaleType 图片缩放类型
     */
    void setImageScaleType(int scaleType);

    void setIndicatorHighlightColor(int color);
    int getIndicatorHighlightColor();
    float getIndicatorCornerRadius();
    void setIndicatorCornerRadius(float radius);
    void setIndicatorHorizontalPadding(int padding);
    int getIndicatorHorizontalPadding();
    void setIndicatorVerticalPadding(int padding);
    int getIndicatorVerticalPadding();

}
