import { Suspense } from "react";
import { AuthForm } from "@/components/auth/AuthForm";

export const metadata = { title: "Sign in — Adventure Dose" };

export default function LoginPage() {
  return (
    <div className="auth-wrap">
      <Suspense fallback={<div className="auth-card" />}>
        <AuthForm />
      </Suspense>
    </div>
  );
}
