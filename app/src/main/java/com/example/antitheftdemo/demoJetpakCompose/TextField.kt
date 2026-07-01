package com.example.antitheftdemo.demoJetpakCompose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun HomeScreen() {
    Column(modifier = Modifier.padding(24.dp)) {
        DemoTextField()
    }
}
@Composable
fun DemoTextField() {
    var firstName by remember { mutableStateOf("") }



    TextField(value = firstName, onValueChange = { newName ->
        firstName = newName

    },
        textStyle = TextStyle(color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold),
        label = {Text(text = "FirstName")},
        placeholder = {Text(text = "Enter your firstName")},
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Person") },
        trailingIcon = {
            IconButton(onClick = {
                firstName = ""
            }) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        },


        )

}

@Composable
fun Composable() {
    Spacer(modifier = Modifier.height(24.dp))
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HomeScreen()
}