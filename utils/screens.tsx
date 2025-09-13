import NativeBubble from '@/specs/NativeBubble';
import React from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';

const BubbleScreen = () => {
  const handleClose = () => {
    console.log('Closing bubble from React Native...');
    if (NativeBubble) {
      // 3. Вызовите ваш нативный метод
      NativeBubble.hideBubble();
    } else {
      console.error('NativeBubble module is not available');
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Привет из Бабла!</Text>
      <Text>Это React Native компонент.</Text>
      <Button title="Закрыть" onPress={handleClose} /> {/* 4. Используйте обработчик */}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f0f0f0',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 10,
  },
});

export default BubbleScreen;