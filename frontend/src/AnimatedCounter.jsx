import React, { useEffect, useState, useRef } from 'react';

const AnimatedCounter = ({ value, duration = 5000 }) => {
  // Validate value to prevent NaN
  const safeValue = typeof value === 'number' && !isNaN(value) ? value : 0;
  
  const [displayValue, setDisplayValue] = useState(safeValue);
  const startValue = useRef(safeValue);
  const startTime = useRef(null);
  const animationFrameId = useRef(null);
  const lastTargetValue = useRef(safeValue);

  useEffect(() => {
    // Determine the type of update:
    // 1. Small increment (<= 5): Instant/Fast (e.g. user click)
    // 2. Large increment (> 5): Smooth animation over duration (server sync)
    
    const diff = safeValue - displayValue;
    
    // If value hasn't changed effectively, do nothing
    if (safeValue === lastTargetValue.current) return;
    lastTargetValue.current = safeValue;
    
    // Cancel any existing animation
    if (animationFrameId.current) {
      cancelAnimationFrame(animationFrameId.current);
    }

    // Logic for duration:
    // If diff is small (e.g. <= 5), use very short duration for responsiveness
    // If diff is large, use the passed duration (5000ms as requested)
    const effectiveDuration = (diff > 0 && diff <= 5) ? 100 : duration;
    
    startValue.current = displayValue;
    startTime.current = null;

    const animate = (timestamp) => {
      if (!startTime.current) startTime.current = timestamp;
      const progress = timestamp - startTime.current;
      
      const percent = Math.min(progress / effectiveDuration, 1);
      
      const nextValue = Math.floor(startValue.current + (safeValue - startValue.current) * percent);
      
      setDisplayValue(nextValue);

      if (progress < effectiveDuration) {
        animationFrameId.current = requestAnimationFrame(animate);
      } else {
        setDisplayValue(safeValue);
      }
    };

    animationFrameId.current = requestAnimationFrame(animate);

    return () => {
      if (animationFrameId.current) {
        cancelAnimationFrame(animationFrameId.current);
      }
    };
  }, [safeValue, duration]);

  return <>{displayValue.toLocaleString('fa-IR')}</>;
};

export default AnimatedCounter;
