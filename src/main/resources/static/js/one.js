// 全局变量和函数
let currentIndex = 0;
let slides = [];
let slidesContainer = null;

// 确保DOM加载完成后执行
window.onload = function() {
    slides = document.querySelectorAll('.slide');
    slidesContainer = document.querySelector('.slides');
    
    console.log('Slides found:', slides.length);
    console.log('Slides container:', slidesContainer);
    
    // 初始化显示第一张幻灯片
    showSlide(currentIndex);
    
    // 每3秒切换一次
    setInterval(nextSlide, 3000);
    
    console.log('Slider initialized');
};

function showSlide(index) {
    const totalSlides = slides.length;
    const offset = index * -100;
    if (slidesContainer) {
        slidesContainer.style.transform = `translateX(${offset}%)`;
        console.log('Showing slide:', index, 'Offset:', offset);
    } else {
        console.error('Slides container not found');
    }
}

function nextSlide() {
    currentIndex = (currentIndex + 1) % slides.length;
    showSlide(currentIndex);
}

function prevSlide() {
    currentIndex = (currentIndex - 1 + slides.length) % slides.length;
    showSlide(currentIndex);
}
