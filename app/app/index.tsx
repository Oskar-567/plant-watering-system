import { Redirect } from 'expo-router';

// Auth check will be added in issue #59.
// For now, always redirect to Dashboard.
export default function Index() {
  return <Redirect href="/(tabs)" />;
}
