import { useState } from "react";
import {
  Button,
  Card,
  FieldError,
  Form,
  Input,
  Label,
  Link,
  TextField,
} from "@heroui/react";

import { Link003 } from "@/components/ui/skiper-ui/skiper40";

type Screen = "welcome" | "login";

function App() {
  const [screen, setScreen] = useState<Screen>("welcome");
  const [isPending, setIsPending] = useState(false);
  const [signedInEmail, setSignedInEmail] = useState<string | null>(null);

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(event.currentTarget));

    setIsPending(true);
    // TODO: replace with a real auth call (e.g. Firebase signInWithEmailAndPassword)
    setTimeout(() => {
      setIsPending(false);
      setSignedInEmail(String(data.email));
    }, 1200);
  };

  if (signedInEmail) {
    return (
      <main className="flex min-h-dvh flex-col items-center justify-center bg-background p-6">
        <Card className="w-full max-w-sm">
          <Card.Header>
            <Card.Title>Signed in</Card.Title>
            <Card.Description>Welcome, {signedInEmail}.</Card.Description>
          </Card.Header>
          <Card.Footer>
            <Button fullWidth size="lg" variant="outline" onPress={() => setSignedInEmail(null)}>
              Sign out
            </Button>
          </Card.Footer>
        </Card>
      </main>
    );
  }

  if (screen === "welcome") {
    return (
      <main className="flex min-h-dvh flex-col items-center bg-background px-6 pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-[max(1.5rem,env(safe-area-inset-top))]">
        <div className="flex flex-1 flex-col items-center justify-center gap-3 text-center">
          <span className="text-5xl font-bold tracking-tight text-foreground">Kinbridge</span>
          <p className="max-w-xs text-base text-muted-foreground">
            Stay close to the people who matter.
          </p>
        </div>
        <div className="flex w-full max-w-sm flex-col items-center gap-4">
          <Button fullWidth size="lg" onPress={() => setScreen("login")}>
            Get started
          </Button>
          <Link003 className="text-sm text-muted-foreground" href="mailto:support@kinbridge.app">
            Contact support
          </Link003>
        </div>
      </main>
    );
  }

  return (
    <main className="flex min-h-dvh flex-col items-center bg-background px-6 pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-[max(1.5rem,env(safe-area-inset-top))]">
      <div className="flex w-full max-w-sm flex-1 flex-col justify-center">
        <Card className="w-full">
          <Card.Header>
            <Card.Title>Welcome back</Card.Title>
            <Card.Description>Sign in to your account to continue</Card.Description>
          </Card.Header>
          <Card.Content>
            <Form className="flex flex-col gap-4" onSubmit={handleSubmit}>
              <TextField fullWidth isRequired name="email" type="email">
                <Label>Email</Label>
                <Input autoComplete="email" inputMode="email" placeholder="you@example.com" />
                <FieldError>Please enter a valid email address</FieldError>
              </TextField>
              <TextField fullWidth isRequired minLength={8} name="password" type="password">
                <Label>Password</Label>
                <Input autoComplete="current-password" placeholder="••••••••" />
                <FieldError>Password must be at least 8 characters</FieldError>
              </TextField>
              <Button fullWidth isPending={isPending} size="lg" type="submit">
                {isPending ? "Signing in…" : "Sign in"}
              </Button>
            </Form>
          </Card.Content>
          <Card.Footer className="justify-between text-sm">
            <Link href="#">Forgot password?</Link>
            <Link href="#">Create account</Link>
          </Card.Footer>
        </Card>
      </div>
      <div className="w-full max-w-sm pt-4">
        <Button fullWidth size="lg" variant="ghost" onPress={() => setScreen("welcome")}>
          Back
        </Button>
      </div>
    </main>
  );
}

export default App;
