package com.lx.multimedialearn.usecamera.render;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.lx.multimedialearn.utils.GlUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * GlsurfaceView进行图像预览
 * 1. 实例化SurfaceTexture，获取camera的预览数据：思考，这里能不能让三个view公用这一个surfacetexture
 * 2. 使用Render进行绘画，画出预览数据，这里可以对图像进行处理
 * 思考：自定义SurfaceTexture获取摄像头数据，存储在显存里，并有一个TextureID，通过updateTexImage()，把camera中的数据生成最新的纹理，使用glsl语言把内容画在glsurfaceview上
 *
 * @author lixiao
 * @since 2017-09-17 15:13
 */
public class CameraRender implements GLSurfaceView.Renderer {
    private SurfaceTexture mSurfaceTexture; //使用共享，在应用层创建，并传进来获取摄像头数据
    private int mTextureID; //预览Camera画面对应的纹理id，通过该id画图

    public CameraRender(SurfaceTexture surfaceTexture, int textureID) {
        this.mSurfaceTexture = surfaceTexture;
        this.mTextureID = textureID;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    }

    /***************************画笔所需要的相关参数，整合在这里*************************************/
    private String vertextShader =
            "uniform mat4 uMVPMatrix;\n" +
                    "attribute vec4 vPosition;\n" +
                    "attribute vec2 inputTextureCoordinate;\n" +
                    "varying vec2 textureCoordinate;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    gl_Position =  vPosition * uMVPMatrix ;\n" +
                    "    textureCoordinate = inputTextureCoordinate;\n" +
                    "}";

    private String fragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 textureCoordinate;\n" +
                    "uniform samplerExternalOES s_texture;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D( s_texture, textureCoordinate );\n" +
                    "}";


    //设置opengl的相关程序，以及初始化变量，然后执行，就是画图的全过程
    private FloatBuffer vertexBuffer; // 顶点缓存
    private FloatBuffer mTextureCoordsBuffer; // 纹理坐标映射缓存
    private ShortBuffer drawListBuffer; // 绘制顺序缓存
    private int mProgram; // OpenGL 可执行程序
    private int mPositionHandle;
    private int mTextureCoordHandle;
    private int mMVPMatrixHandle;

    private short drawOrder[] = {0, 2, 1, 0, 3, 2}; // 绘制顶点的顺序


    private final int COORDS_PER_VERTEX = 2; // 每个顶点的坐标数
    private final int vertexStride = COORDS_PER_VERTEX * 4; //每个坐标数4 bytes，那么每个顶点占8 bytes
    private float mVertices[] = new float[8];
    private float mTextureCoords[] = new float[8];
    private float mTextHeightRatio = 0.1f;
    private float[] mMVP = new float[16];

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        gl.glViewport(0, 0, width, height);// GlSurfaceView基本参数设置
        //根据TextureID设置画图的初始参数,初始化画图程序，参数
        //(1)根据vertexShader，fragmentShader设置绘图程序
        mProgram = GlUtil.createProgram(vertextShader, fragmentShader);
        //(2)获取gl程序中参数，进行赋值
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        mTextureCoordHandle = GLES20.glGetAttribLocation(mProgram, "inputTextureCoordinate");
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        //(3)初始化显示的顶点等坐标，在这些坐标范围内显示相机预览数据?
        updateVertices();
        setTexCoords();
        //(4)设置画图坐标?
        ByteBuffer bf = ByteBuffer.allocateDirect(drawOrder.length * 2);
        bf.order(ByteOrder.nativeOrder());
        drawListBuffer = bf.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        //mat4f_LoadOrtho(-1.0f, 1.0f, -1.0f, 1.0f, -1.0f, 1.0f, mMVP);
        mat4f_LoadOrtho(1.0f, -1.0f, 1.0f, -1.0f, 1.0f, -1.0f, mMVP);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        synchronized (CameraRender.class) {
            GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f); //清理屏幕,设置屏幕为白板
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            mSurfaceTexture.attachToGLContext(mTextureID);
            mSurfaceTexture.updateTexImage(); //拿到最新的数据

            //绘制预览数据
            GLES20.glUseProgram(mProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
            // Enable a handle to the triangle vertices
            GLES20.glEnableVertexAttribArray(mPositionHandle);
            // Prepare the <insert shape here> coordinate data
            GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
            GLES20.glEnableVertexAttribArray(mTextureCoordHandle);
            GLES20.glVertexAttribPointer(mTextureCoordHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, mTextureCoordsBuffer);
            // Apply the projection and view transformation //进行图形的转换
            GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVP, 0);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
            // Disable vertex array
            GLES20.glDisableVertexAttribArray(mPositionHandle);
            GLES20.glDisableVertexAttribArray(mTextureCoordHandle);
            mSurfaceTexture.detachFromGLContext();
        }
    }

    /*******************************初始化Shader程序相关函数*****************************************/
    public void resetMatrix() {
        mat4f_LoadOrtho(-1.0f, 1.0f, -1.0f, 1.0f, -1.0f, 1.0f, mMVP);
    }

    private static void mat4f_LoadOrtho(float left, float right, float bottom, float top, float near, float far, float[] mout) {
        float r_l = right - left;
        float t_b = top - bottom;
        float f_n = far - near;
        float tx = -(right + left) / (right - left);
        float ty = -(top + bottom) / (top - bottom);
        float tz = -(far + near) / (far - near);

        mout[0] = 2.0f / r_l;
        mout[1] = 0.0f;
        mout[2] = 0.0f;
        mout[3] = 0.0f;

        mout[4] = 0.0f;
        mout[5] = 2.0f / t_b;
        mout[6] = 0.0f;
        mout[7] = 0.0f;

        mout[8] = 0.0f;
        mout[9] = 0.0f;
        mout[10] = -2.0f / f_n;
        mout[11] = 0.0f;

        mout[12] = tx;
        mout[13] = ty;
        mout[14] = tz;
        mout[15] = 1.0f;
    }

    private void updateVertices() {
        final float w = 1.0f;
        final float h = 1.0f;
        mVertices[0] = -w;
        mVertices[1] = h;
        mVertices[2] = -w;
        mVertices[3] = -h;
        mVertices[4] = w;
        mVertices[5] = -h;
        mVertices[6] = w;
        mVertices[7] = h;
        vertexBuffer = ByteBuffer.allocateDirect(mVertices.length * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().put(mVertices);
        vertexBuffer.position(0);
    }

    private void setTexCoords() {
//        mTextureCoords[0] = 0;
//        mTextureCoords[1] = 1 - mTextHeightRatio;
//        mTextureCoords[2] = 1;
//        mTextureCoords[3] = 1 - mTextHeightRatio;
//        mTextureCoords[4] = 1;
//        mTextureCoords[5] = 0 + mTextHeightRatio;
//        mTextureCoords[6] = 0;
//        mTextureCoords[7] = 0 + mTextHeightRatio;

        mTextureCoords[0] = 0;
        mTextureCoords[1] = 0 + mTextHeightRatio;
        mTextureCoords[2] = 1;
        mTextureCoords[3] = 0 + mTextHeightRatio;
        mTextureCoords[4] = 1;
        mTextureCoords[5] = 1 - mTextHeightRatio;
        mTextureCoords[6] = 0;
        mTextureCoords[7] = 1 - mTextHeightRatio;
        mTextureCoordsBuffer = ByteBuffer.allocateDirect(mTextureCoords.length * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().put(mTextureCoords);
        mTextureCoordsBuffer.position(0);
    }
}