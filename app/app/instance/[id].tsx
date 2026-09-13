import { useLocalSearchParams } from 'expo-router';
import { Text, View } from 'react-native';

export default function InstanceDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>Instance {id}</Text>
    </View>
  );
}
